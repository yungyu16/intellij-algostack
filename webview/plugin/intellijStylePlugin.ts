import type {Plugin} from 'vite';

/**
 * intellijStyle.css 注入插件。
 *
 * Kotlin 侧 WebDocumentView 动态生成 intellijStyle.css（编辑器配色、字体、--darkMode 变量），
 * 本插件在页面 head 注入其 link 与同步的 onload 回调，确保回调在 link 加载前已定义。
 * CSS 加载与 module 执行不能假定固定先后顺序；若改为模块监听，须同时处理样式已加载的情况。
 * 页面共享运行时（滚动条、缩放、宿主通信）由各页面入口显式 import plugin/runtime.ts 引入。
 */
export function intellijStylePostPlugin(): Plugin {
    return {
        name: 'intellij-style-post-plugin',
        transformIndexHtml: {
            order: 'post',
            handler: (html) => ({
                html,
                tags: [
                    {
                        tag: 'script',
                        children: `
                            window.onIntellijStyleLoaded = function () {
                                const isDarkMode = getComputedStyle(document.documentElement)
                                    .getPropertyValue('--darkMode')
                                    .trim() === '1';
                                document.documentElement.setAttribute('data-theme', isDarkMode ? 'dark' : 'light');
                            };
                        `,
                    },
                    {
                        tag: 'link',
                        attrs: {
                            id: 'intellijStyle',
                            rel: 'stylesheet',
                            type: 'text/css',
                            href: '/intellijStyle.css',
                            onload: 'onIntellijStyleLoaded()',
                        },
                    },
                ],
            }),
        },
    };
}
