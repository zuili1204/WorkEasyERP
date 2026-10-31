import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// HBuilderX 根目录布局：源码不在 src/ 下，构建时通过 UNI_INPUT_DIR 指向工程根
export default defineConfig({
  plugins: [uni()],
})
