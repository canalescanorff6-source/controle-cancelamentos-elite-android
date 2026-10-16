'use strict';
/**
 * Assinatura estável para o Android Elite com 1 GitHub Secret.
 * A chave privada PKCS12 fica SOMENTE em memória/arquivos temporários do runner.
 * A cópia criptografada, com fingerprint público, é versionada em GitHub.
 */
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const child = require('node:child_process');
const root = path.resolve(__dirname, '..');
const encryptedPath = path.join(root, '.github/android-signing/elite-release.enc.json');
const applicationId = 'br.com.controlecancelamentos';
const alias = 'controleelite';
const aad = 'CONTROLE-ELITE|' + applicationId + '|v1';

function derive(master, label) {
  return crypto.createHash('sha256').update(label + '\0' + master, 'utf8').digest();
}
function readMaster() {
  const master = process.env.ANDROID_RELEASE_SECRET || '';
  if (!/^[A-Za-z0-9_-]{40,90}$/.test(master)) {
    throw Error('ANDROID_RELEASE_SECRET ausente ou invalido; configure o segredo em Settings > Secrets and variables > Actions.');
  }
  return master;
}
function passwordFrom(master) {
  return derive(master, 'ELITE-ANDROID/password-v1').toString('hex').slice(0, 48);
}
function run(file, args, env = process.env) {
  return child.execFileSync(file, args, { env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
}
function fingerprint(keystore, pass) {
  const output = run('keytool', [
    '-list', '-v', '-storetype', 'PKCS12', '-keystore', keystore,
    '-storepass:env', 'ELITE_KEY_PASS', '-alias', alias
  ], { ...process.env, ELITE_KEY_PASS: pass });
  const match = output.match(/SHA256:\s*([0-9a-f:]+)/i);
  if (!match) throw Error('Certificado Android nao legivel pelo keytool');
  return match[1].replace(/:/g, '').toLowerCase();
}
function readEncrypted() {
  const item = JSON.parse(fs.readFileSync(encryptedPath, 'utf8'));
  if (item.format !== 'elite-android-keystore-aesgcm-v1' ||
      item.applicationId !== applicationId || item.keyAlias !== alias ||
      item.aad !== aad ||
      !/^[a-f0-9]{64}$/.test(item.certificateSha256 || '') ||
      Buffer.from(item.iv || '', 'base64').length !== 12 ||
      Buffer.from(item.tag || '', 'base64').length !== 16 ||
      Buffer.from(item.ciphertext || '', 'base64').length < 500) {
    throw Error('Manifesto da assinatura permanente invalido');
  }
  return item;
}
function init() {
  if (fs.existsSync(encryptedPath)) throw Error('Identidade Android ja existe. Nao recriar ou sobrescrever a assinatura.');
  const master = readMaster();
  const pass = passwordFrom(master);
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'elite-initialize-'));
  const keystore = path.join(tempDir, 'elite-release.p12');
  try {
    run('keytool', [
      '-genkeypair', '-alias', alias, '-keyalg', 'RSA', '-keysize', '3072',
      '-sigalg', 'SHA256withRSA', '-validity', '10000', '-storetype', 'PKCS12',
      '-keystore', keystore, '-storepass:env', 'ELITE_KEY_PASS',
      '-keypass:env', 'ELITE_KEY_PASS',
      '-dname', 'CN=Controle Cancelamentos Elite,OU=Android,O=Controle Elite,C=BR',
      '-noprompt'
    ], { ...process.env, ELITE_KEY_PASS: pass });
    const cert = fingerprint(keystore, pass);
    const iv = crypto.randomBytes(12);
    const key = derive(master, 'ELITE-ANDROID/keystore-v1');
    const encrypt = crypto.createCipheriv('aes-256-gcm', key, iv);
    encrypt.setAAD(Buffer.from(aad, 'utf8'));
    const plain = fs.readFileSync(keystore);
    const encrypted = Buffer.concat([encrypt.update(plain), encrypt.final()]);
    const metadata = {
      format: 'elite-android-keystore-aesgcm-v1', applicationId, keyAlias: alias,
      aad, certificateSha256: cert,
      iv: iv.toString('base64'), tag: encrypt.getAuthTag().toString('base64'),
      ciphertext: encrypted.toString('base64')
    };
    fs.mkdirSync(path.dirname(encryptedPath), { recursive: true });
    fs.writeFileSync(encryptedPath, JSON.stringify(metadata, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
    process.stdout.write('Identidade de assinatura criada e cifrada. SHA256 publico do certificado: ' + cert + '\n');
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}
function restore(master, encrypted) {
  const decrypt = crypto.createDecipheriv('aes-256-gcm', derive(master, 'ELITE-ANDROID/keystore-v1'), Buffer.from(encrypted.iv, 'base64'));
  decrypt.setAAD(Buffer.from(aad, 'utf8'));
  decrypt.setAuthTag(Buffer.from(encrypted.tag, 'base64'));
  const keyData = Buffer.concat([decrypt.update(Buffer.from(encrypted.ciphertext, 'base64')), decrypt.final()]);
  if (keyData.length < 600) throw Error('Keystore decifrada muito pequena');
  return keyData;
}
function buildTools() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (!sdk) throw Error('SDK Android nao localizado');
  const folder = path.join(sdk, 'build-tools');
  const found = fs.readdirSync(folder).filter(n => /^\d/.test(n)).sort((a,b)=>a.localeCompare(b, undefined, {numeric:true}));
  const ver = found[found.length - 1];
  if (!ver) throw Error('Android build-tools ausentes');
  const zipalign = path.join(folder, ver, 'zipalign');
  const apksigner = path.join(folder, ver, 'apksigner');
  if (!fs.existsSync(zipalign) || !fs.existsSync(apksigner)) throw Error('zipalign/apksigner ausentes');
  return {zipalign, apksigner};
}
function sign() {
  const master = readMaster();
  const data = readEncrypted();
  const pass = passwordFrom(master);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'elite-signing-'));
  const keyPath = path.join(tmp, 'release.p12');
  const aligned = path.join(tmp, 'aligned.apk');
  const source = path.join(root, 'app/build/outputs/apk/release/app-release-unsigned.apk');
  const tag = process.env.ELITE_TAG || '';
  if (!/^v\d+\.\d+\.\d+$/.test(tag)) throw Error('ELITE_TAG nao corresponde a uma versao oficial');
  try {
    const keyBytes = restore(master, data);
    fs.writeFileSync(keyPath, keyBytes, { mode: 0o600 });
    if (fingerprint(keyPath, pass) !== data.certificateSha256) {
      throw Error('Certificado diferente da identidade permanente; assinatura cancelada.');
    }
    if (!fs.existsSync(source)) throw Error('APK release nao compilado');
    const tools = buildTools();
    run(tools.zipalign, ['-p', '-f', '4', source, aligned]);
    const outDir = path.join(root, 'dist');
    fs.mkdirSync(outDir, { recursive: true });
    const fileName = 'Controle_Cancelamentos_Elite_' + tag + '.apk';
    const output = path.join(outDir, fileName);
    const env = { ...process.env, ELITE_KEY_PASS: pass };
    run(tools.apksigner, [
      'sign', '--ks', keyPath, '--ks-type', 'PKCS12', '--ks-key-alias', alias,
      '--ks-pass', 'env:ELITE_KEY_PASS', '--key-pass', 'env:ELITE_KEY_PASS',
      '--out', output, aligned
    ], env);
    const proof = run(tools.apksigner, ['verify', '--verbose', '--print-certs', output], env);
    const digests = (proof.match(/(?:[0-9a-f]{2}:){31}[0-9a-f]{2}|[0-9a-f]{64}/gi) || [])
      .map(x=>x.replace(/:/g,'').toLowerCase());
    if (!digests.includes(data.certificateSha256)) {
      fs.rmSync(output, {force:true});
      throw Error('APK com certificado inesperado; publicacao bloqueada');
    }
    const checksum = crypto.createHash('sha256').update(fs.readFileSync(output)).digest('hex');
    fs.writeFileSync(path.join(outDir, 'checksum-sha256.txt'), checksum + '  ' + fileName + '\n');
    process.stdout.write('APK oficial assinado, integridade e certificado validados.\n');
  } finally {
    fs.rmSync(tmp, { recursive:true, force:true });
  }
}
try {
  const command = process.argv[2] || 'check';
  if (command === 'init') init();
  else if (command === 'sign') sign();
  else if (command === 'check') {
    if (fs.existsSync(encryptedPath)) {
      const info = readEncrypted();
      process.stdout.write('Certificado publico da identidade Elite: ' + info.certificateSha256 + '\n');
    } else process.stdout.write('Assinatura permanente ainda nao inicializada.\n');
  } else throw Error('Comando desconhecido. Use check, init ou sign');
} catch (error) {
  process.stderr.write('Falha na assinatura Elite: ' + error.message + '\n');
  process.exitCode = 1;
}
