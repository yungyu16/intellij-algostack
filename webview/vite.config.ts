import {defineConfig} from 'vite';
import {dirname, resolve} from 'node:path'
import {fileURLToPath} from "node:url";
import {intellijStylePostPlugin} from './plugin/intellijStylePlugin';

// 构建配置基于上游 CodeEpiphany（TypeFox 系模板，MIT）精简：仅保留两个页面的产物入口与
// intellijStyle.css 注入；dev server 相关的 COOP/COEP、optimizeDeps 等浏览器调试配置已移除。
const __dirname = dirname(fileURLToPath(import.meta.url))

export const definedViteConfig = defineConfig({
    build: {
        target: 'ES2022',
        outDir: "../build/webviewResources/webview",
        rollupOptions: {
            input: {
                challengeDescription: resolve(__dirname, 'challengeDescription/index.html'),
                leetCodeSolutionArticle: resolve(__dirname, 'leetCodeSolutionArticle/index.html'),
            },
            output: {
                // 显式命名共享运行时，便于检查产物；不依赖压缩后的函数名判断模块是否存在。
                manualChunks(id): string | undefined {
                    if (id.endsWith('plugin/runtime.ts')) return 'runtime';
                    return undefined;
                },
            },
        },
        assetsDir: 'assets',
    },
    server: {
        port: 5173,
        host: true,
    },
    plugins: [
        intellijStylePostPlugin(),
    ],
});

export default definedViteConfig;
