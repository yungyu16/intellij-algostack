import type {Plugin} from 'vite';

/**
 * intellijStyle.css 注入插件。
 *
 * Kotlin 侧 WebDocumentView 动态生成 intellijStyle.css（编辑器配色、字体、--darkMode 变量），
 * 本插件在页面 head 注入其 link 与同步的 onload 回调。onIntellijStyleLoaded 必须保持同步内联——
 * CSS onload 早于 module 脚本执行，移入 JS module 会丢失主题标记时机。
 * 页面共享运行时（滚动条、缩放、宿主通信）由各页面入口显式 import plugin/runtime.js 引入。
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
