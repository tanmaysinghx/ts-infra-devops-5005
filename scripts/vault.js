import crypto from 'crypto';
import fs from 'fs';

// File format: [16-byte IV][AES-256-CBC ciphertext]. Key = scrypt(password, 'salt', 32).
// The static salt is kept for compatibility with every .env.enc already committed;
// changing it requires re-encrypting all of them.
const ALGORITHM = 'aes-256-cbc';
const IV_LENGTH = 16; // For AES, this is always 16
const KEY_LENGTH = 32;
const SALT = 'salt';
const ENC_SUFFIX = '.enc';

function deriveKey(password) {
    return crypto.scryptSync(password, SALT, KEY_LENGTH);
}

/**
 * Encrypts a file, writing <filePath>.enc next to it
 */
export function encrypt(filePath, password) {
    const input = fs.readFileSync(filePath);
    const iv = crypto.randomBytes(IV_LENGTH);
    const cipher = crypto.createCipheriv(ALGORITHM, deriveKey(password), iv);
    const encrypted = Buffer.concat([iv, cipher.update(input), cipher.final()]);
    fs.writeFileSync(filePath + ENC_SUFFIX, encrypted);
    console.log(`✅ Encrypted: ${filePath} -> ${filePath}${ENC_SUFFIX}`);
}

/**
 * Decrypts a <file>.enc, writing <file> next to it
 */
export function decrypt(encFilePath, password) {
    if (!encFilePath.endsWith(ENC_SUFFIX)) {
        throw new Error(`Expected a file ending in "${ENC_SUFFIX}", got: ${encFilePath}`);
    }
    const input = fs.readFileSync(encFilePath);
    if (input.length <= IV_LENGTH) {
        throw new Error(`File is too short to be a vault file: ${encFilePath}`);
    }
    const iv = input.subarray(0, IV_LENGTH);
    const encryptedData = input.subarray(IV_LENGTH);
    const decipher = crypto.createDecipheriv(ALGORITHM, deriveKey(password), iv);
    const decrypted = Buffer.concat([decipher.update(encryptedData), decipher.final()]);
    const targetPath = encFilePath.slice(0, -ENC_SUFFIX.length);
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
    else {
        console.error(`Invalid action "${action}". Use "encrypt" or "decrypt".`);
        process.exit(1);
    }
} catch (err) {
    console.error('❌ Operation failed:', err.message);
    process.exit(1);
}
