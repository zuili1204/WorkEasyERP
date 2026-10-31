import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// HBuilderX 根目录布局没有 src/，uni CLI 默认读取 src，这里显式指定工程根为输入目录
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const cli = path.join(root, 'node_modules', '@dcloudio', 'vite-plugin-uni', 'bin', 'uni.js')

const child = spawn(process.execPath, [cli, ...process.argv.slice(2)], {
  cwd: root,
  stdio: 'inherit',
  env: { ...process.env, UNI_INPUT_DIR: root },
})
child.on('exit', (code) => process.exit(code ?? 1))
