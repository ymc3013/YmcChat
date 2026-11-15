import { Pool } from "pg";

export const database = new Pool({
    host: process.env.DATABASE_HOST,
    port: Number(process.env.DATABASE_PORT || 5432),
    database: process.env.DATABASE_NAME,
    user: process.env.DATABASE_USER,
    password: process.env.DATABASE_PASSWORD,

    max: 5,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000
});

database.on("error", (error) => {
    console.error("Erreur PostgreSQL :", error);
});
