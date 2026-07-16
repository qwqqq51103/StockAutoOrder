import { cp, mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const rootDir = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(rootDir, "..");
const sourceDir = path.resolve(projectDir, "dist");
const targetDir = path.resolve(projectDir, "..", "src", "main", "resources", "static");

await rm(targetDir, { recursive: true, force: true });
await mkdir(targetDir, { recursive: true });
await cp(sourceDir, targetDir, { recursive: true, force: true });

console.log(`Synced ${sourceDir} -> ${targetDir}`);
