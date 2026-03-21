import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

const ALGORITHM = 'aes-256-cbc';
const IV_LENGTH = 16; // For AES, this is always 16

/**
 * Encrypts a file
 */
export function encrypt(filePath, password) {
    const input = fs.readFileSync(filePath);
    const iv = crypto.randomBytes(IV_LENGTH);
    const key = crypto.scryptSync(password, 'salt', 32);
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    const encrypted = Buffer.concat([iv, cipher.update(input), cipher.final()]);
    fs.writeFileSync(filePath + '.enc', encrypted);
    console.log(`✅ Encrypted: ${filePath} -> ${filePath}.enc`);
}

/**
 * Decrypts a file
 */
export function decrypt(encFilePath, password) {
    const input = fs.readFileSync(encFilePath);
    const iv = input.slice(0, IV_LENGTH);
    const encryptedData = input.slice(IV_LENGTH);
    const key = crypto.scryptSync(password, 'salt', 32);
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    const decrypted = Buffer.concat([decipher.update(encryptedData), decipher.final()]);
    const targetPath = encFilePath.replace('.enc', '');
    fs.writeFileSync(targetPath, decrypted);
    console.log(`✅ Decrypted: ${encFilePath} -> ${targetPath}`);
}

// CLI usage
const args = process.argv.slice(2);
if (args.length < 3) {
    console.log('Usage: node vault.js <encrypt|decrypt> <file_path> <password>');
    process.exit(1);
}

const [action, file, pwd] = args;
try {
    if (action === 'encrypt') encrypt(file, pwd);
    else if (action === 'decrypt') decrypt(file, pwd);
    else console.error('Invalid action');
} catch (err) {
    console.error('❌ Operation failed:', err.message);
    process.exit(1);
}
