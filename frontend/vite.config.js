import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/

// 代理属于服务端转发，去掉浏览器带来的 Origin，
// 否则通过主机 IP 访问时后端 CORS 会把它当作未授权的跨域请求返回 403
function stripOrigin(proxy) {
  proxy.on('start', (req) => {
    delete req.headers.origin
  })
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 其他设备通过主机 IP 访问时，接口统一走同源代理，
    // 避免浏览器把 localhost 解析成访问者自己的机器
    proxy: {
      '/agent-api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/agent-api/, ''),
        configure: stripOrigin
      },
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        configure: stripOrigin
      }
    }
  }
})
