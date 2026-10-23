#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';

const defaultDirectory = path.join(os.homedir(), '.local/state/yy-leetcode/bridge');

export function readDescriptor(file) {
    const stat = fs.lstatSync(file);
    if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0 || stat.uid !== process.getuid())
        throw new Error('桥接描述文件权限或所有者不符');
    const d = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (d.version !== 1 || !Number.isInteger(d.port) || d.port < 1 || d.port > 65535
        || !Number.isInteger(d.pid) || !/^[0-9a-f]{64}$/.test(d.token)) throw new Error('桥接描述文件格式无效');
    return d;
}

export async function request(d, route, body, timeout = 120000) {
    const response = await fetch(`http://127.0.0.1:${d.port}${route}`, {
        method: body ? 'POST' : 'GET', redirect: 'error', signal: AbortSignal.timeout(timeout),
        headers: {Authorization: `Bearer ${d.token}`, ...(body ? {'Content-Type': 'application/json'} : {})},
        body: body ? JSON.stringify(body) : undefined,
    });
    const result = await response.json();
    if (!response.ok) throw new Error(`桥接 HTTP ${response.status}: ${JSON.stringify(result)}`);
    return result;
}

export async function discover(project, directory = defaultDirectory) {
    const found = [];
    if (!fs.existsSync(directory)) return found;
    for (const name of fs.readdirSync(directory).filter(n => /^\d+\.json$/.test(n))) {
        try {
            const descriptor = readDescriptor(path.join(directory, name));
            const result = await request(descriptor, '/status', undefined, 2000);
            if (result.version === 1 && Array.isArray(result.projects) && result.projects.includes(project))
                found.push(descriptor);
        } catch { /* 崩溃后的过期描述文件或不合规文件不会作为连接目标，也不自动删除。 */
        }
    }
    return found;
}

export async function initializeChallenge(project, slug, language, directory = defaultDirectory, dojo = 'leetcodecn') {
    if (!['leetcodecn', 'leetcode'].includes(dojo) || !['java', 'go'].includes(language) || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) || slug.length > 160)
        throw new Error('需要合法 slug，以及 java 或 go 语言');
    project = fs.realpathSync(project);
    const matches = await discover(project, directory);
    if (matches.length !== 1) throw new Error(matches.length ? '多个 IDEA 实例打开了同一项目，停止以避免选错' : '未找到该项目的可用桥接入口；请安装补丁并重新打开项目');
    return request(matches[0], '/initialize-challenge', {project, slug, language, ...(dojo === 'leetcodecn' ? {} : {dojo})});
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        const args = process.argv.slice(2);
        const value = flag => {
            const i = args.indexOf(flag);
            return i < 0 ? undefined : args[i + 1];
        };
        const project = fs.realpathSync(value('--project') ?? process.cwd());
        if (args[0] === 'status') {
            const matches = await discover(project);
            console.log(JSON.stringify({project, instances: matches.map(d => ({pid: d.pid, port: d.port}))}, null, 2));
            if (matches.length !== 1) process.exitCode = 1;
        } else if (args[0] === 'initialize') {
            const slug = value('--slug'), language = value('--language');
            if (!slug || !language) throw new Error('initialize 需要 --slug 和 --language');
            const result = await initializeChallenge(project, slug, language, defaultDirectory, value('--dojo') ?? 'leetcodecn');
            console.log(JSON.stringify(result, null, 2));
            if (!['created', 'already_exists'].includes(result.status)) process.exitCode = 1;
        } else throw new Error('用法：status [--project 路径] | initialize --slug 题目slug --language java|go [--dojo leetcodecn|leetcode] [--project 路径]');
    } catch (e) {
        console.error(e.name === 'TimeoutError' ? '等待超时；IDEA 中操作可能仍在进行。先检查文件与关联，不自动重试创建。' : e.message);
        process.exitCode = 1;
    }
}
