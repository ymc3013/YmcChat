import "dotenv/config";
import { createApp } from "./app.js";
import { database } from "./database/connection.js";

async function start() {
    const { app, io } = await createApp();

    const port = Number(process.env.PORT || 3000);
    const host = process.env.HOST || "0.0.0.0";

    try {
        await database.query("SELECT 1");

        await app.listen({
            port,
            host
        });

        app.log.info(`YmcChat démarré sur le port ${port}`);
    } catch (error) {
        app.log.error(error);
        io.close();
        await app.close();
        await database.end();
        process.exit(1);
    }
}

start();
