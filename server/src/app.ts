import Fastify from "fastify";
import cors from "@fastify/cors";
import { Server } from "socket.io";
import jwt from "jsonwebtoken";

import { friendsRoutes } from "./friends/friends.routes.js";
import { database } from "./database/connection.js";
import { authRoutes } from "./auth/auth.routes.js";
import { profileRoutes } from "./profile/profile.routes.js";
import { messagesRoutes } from "./messages/messages.routes.js";

// Connexions actives par utilisateur
const activeUsers = new Map<string, Set<string>>();

export async function createApp() {
    const app = Fastify({
        logger: true,
        bodyLimit: 1024 * 1024
    });

    const allowedOrigins = process.env.CORS_ORIGIN
        ? process.env.CORS_ORIGIN.split(",").map(value => value.trim())
        : false;

    await app.register(cors, {
        origin: allowedOrigins
    });

    const io = new Server(app.server, {
        cors: {
            origin: allowedOrigins
        }
    });

    // Réinitialiser les présences après un redémarrage du serveur
    try {
        await database.query(`
            UPDATE users
            SET status = 'offline',
                last_seen = COALESCE(last_seen, NOW())
            WHERE status = 'online'
        `);
    } catch (error) {
        app.log.error(error, "Impossible de réinitialiser les présences.");
    }

    // AUTHENTIFICATION SOCKET.IO
    io.use((socket, next) => {
        const secret = process.env.JWT_SECRET;
        const token = socket.handshake.auth?.token;

        if (!secret || typeof token !== "string") {
            return next(new Error("Authentification requise."));
        }

        try {
            const decoded = jwt.verify(token, secret);

            if (
                typeof decoded !== "object" ||
                typeof decoded.sub !== "string"
            ) {
                return next(new Error("Token invalide."));
            }

            socket.data.userId = decoded.sub;
            next();

        } catch {
            next(new Error("Session invalide ou expirée."));
        }
    });

    // PAGE D'ACCUEIL
    app.get("/", async () => ({
        name: "YmcChat API",
        version: "1.0.0",
        status: "online"
    }));

    // VÉRIFICATION DU SERVEUR
    app.get("/health", async (_request, reply) => {
        try {
            await database.query("SELECT 1");

            return {
                server: "online",
                database: "connected"
            };

        } catch (error) {
            app.log.error(error);

            return reply.code(503).send({
                server: "online",
                database: "disconnected"
            });
        }
    });

    // ROUTES AUTHENTIFICATION
    await app.register(authRoutes);

    // ROUTES PROFILS
    await app.register(profileRoutes);
    
    await app.register(friendsRoutes);

    // DIRECT MESSAGES
    await messagesRoutes(app, io);

    // PRÉSENCE EN TEMPS RÉEL
    io.on("connection", async (socket) => {
        const userId = socket.data.userId as string;

        const userRoom = `user:${userId}`;

        await socket.join(userRoom);

        // Join every conversation room this user belongs to.
        try {
            const memberships = await database.query(
                "SELECT conversation_id FROM conversation_members WHERE user_id = $1",
                [userId]
            );
            for (const membership of memberships.rows) {
                await socket.join(`conversation:${membership.conversation_id}`);
            }
        } catch (error) {
            app.log.error(error, "Erreur de chargement des salons de conversation.");
        }

        let connections = activeUsers.get(userId);

        if (!connections) {
            connections = new Set<string>();
            activeUsers.set(userId, connections);
        }

        connections.add(socket.id);

        app.log.info(`Connexion utilisateur : ${userId}`);

        // Première connexion de cet utilisateur
        if (connections.size === 1) {
            try {
                await database.query(
                    `UPDATE users
                     SET status = 'online',
                         updated_at = NOW()
                     WHERE id = $1`,
                    [userId]
                );

                io.emit("presence:changed", {
                    userId,
                    status: "online",
                    last_seen: null
                });

            } catch (error) {
                app.log.error(error, "Erreur de mise à jour de présence.");
            }
        }

        // Envoyer la liste des utilisateurs actuellement en ligne
        try {
            const result = await database.query(
                `SELECT id, status, last_seen
                 FROM users
                 WHERE status = 'online'`
            );

            socket.emit("presence:sync", result.rows);

        } catch (error) {
            app.log.error(error, "Erreur de synchronisation des présences.");
        }

        // DÉCONNEXION
        socket.on("disconnect", async () => {
            const currentConnections = activeUsers.get(userId);

            if (!currentConnections) return;

            currentConnections.delete(socket.id);

            // Un autre appareil est encore connecté
            if (currentConnections.size > 0) return;

            activeUsers.delete(userId);

            try {
                const result = await database.query(
                    `UPDATE users
                     SET status = 'offline',
                         last_seen = NOW(),
                         updated_at = NOW()
                     WHERE id = $1
                     RETURNING last_seen`,
                    [userId]
                );

                const lastSeen = result.rows[0]?.last_seen ?? null;

                io.emit("presence:changed", {
                    userId,
                    status: "offline",
                    last_seen: lastSeen
                });

                app.log.info(`Utilisateur hors ligne : ${userId}`);

            } catch (error) {
                app.log.error(error, "Erreur de déconnexion utilisateur.");
            }
        });
    });

    return { app, io };
}

