"""极简 Markdown → HTML（只覆盖本项目发布文案用到的语法），用于本地预览 CurseForge 主页效果。

支持：# ~ ### 标题、--- 分隔线、> 引用、- 无序列表、1. 有序列表、
      | 表格 |、**粗体**、*斜体*、`行内代码`、[文字](链接)
"""
import io
import re
import sys


def inline(text):
    text = (text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;'))
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'(?<!\*)\*([^*]+)\*(?!\*)', r'<em>\1</em>', text)
    return text


def convert(md):
    lines = md.split('\n')
    out = []
    i = 0
    n = len(lines)

    def flush_para(buf):
        if buf:
            out.append('<p>' + inline(' '.join(buf).strip()) + '</p>')
            buf.clear()

    para = []
    while i < n:
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            flush_para(para)
            i += 1
            continue

        if stripped == '---':
            flush_para(para)
            out.append('<hr>')
            i += 1
            continue

        m = re.match(r'^(#{1,3}) (.*)$', stripped)
        if m:
            flush_para(para)
            level = len(m.group(1))
            out.append('<h%d>%s</h%d>' % (level, inline(m.group(2)), level))
            i += 1
            continue

        if stripped.startswith('> '):
            flush_para(para)
            buf = []
            while i < n and lines[i].strip().startswith('> '):
                buf.append(lines[i].strip()[2:])
                i += 1
            out.append('<blockquote>%s</blockquote>' % inline(' '.join(buf)))
            continue

        if stripped.startswith('|'):
            flush_para(para)
            rows = []
            while i < n and lines[i].strip().startswith('|'):
                cells = [c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-{2,}:?', c) for c in cells if c):
                    rows.append(cells)
                i += 1
            if rows:
                head = '<tr>' + ''.join('<th>%s</th>' % inline(c) for c in rows[0]) + '</tr>'
                body = ''.join(
                    '<tr>' + ''.join('<td>%s</td>' % inline(c) for c in r) + '</tr>'
                    for r in rows[1:])
                out.append('<table><thead>%s</thead><tbody>%s</tbody></table>' % (head, body))
            continue

        if re.match(r'^[-*] ', stripped):
            flush_para(para)
            items = []
            while i < n and re.match(r'^[-*] ', lines[i].strip()):
                items.append(lines[i].strip()[2:])
                i += 1
            out.append('<ul>' + ''.join('<li>%s</li>' % inline(x) for x in items) + '</ul>')
            continue

        if re.match(r'^\d+\. ', stripped):
            flush_para(para)
            items = []
            while i < n and re.match(r'^\d+\. ', lines[i].strip()):
                items.append(re.sub(r'^\d+\. ', '', lines[i].strip()))
                i += 1
            out.append('<ol>' + ''.join('<li>%s</li>' % inline(x) for x in items) + '</ol>')
            continue

        para.append(stripped)
        i += 1

    flush_para(para)
    return '\n'.join(out)


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>%(title)s</title>
<style>
  :root { color-scheme: light; }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 40px 16px 80px;
    background: #f4f5f7;
    font: 15px/1.72 -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: #2b2b2b;
  }
  .page { max-width: 860px; margin: 0 auto; background: #fff;
          border-radius: 10px; padding: 44px 52px 56px;
          box-shadow: 0 1px 3px rgba(0,0,0,.08), 0 10px 30px rgba(0,0,0,.06); }
  h1 { font-size: 32px; line-height: 1.25; margin: 0 0 6px; color: #f16436; letter-spacing: -.4px; }
  h1 + p { font-size: 17px; color: #444; margin: 10px 0 18px; }
  h2 { font-size: 21px; margin: 44px 0 14px; padding-bottom: 8px;
       border-bottom: 1px solid #ebebeb; color: #1f1f1f; }
  h3 { font-size: 17px; margin: 28px 0 10px; color: #444; }
  p { margin: 12px 0; }
  a { color: #f16436; text-decoration: none; }
  a:hover { text-decoration: underline; }
  ul, ol { margin: 12px 0; padding-left: 24px; }
  li { margin: 7px 0; }
  code { background: #f2f3f5; border: 1px solid #e6e7ea; border-radius: 4px;
         padding: 1px 5px; font: 13px/1.5 "SFMono-Regular", Consolas, monospace; color: #c0341d; }
  blockquote { margin: 20px 0; padding: 12px 18px; background: #fff7f3;
               border-left: 3px solid #f16436; border-radius: 0 6px 6px 0; color: #4a3a34; }
  blockquote p { margin: 0; }
  hr { border: 0; border-top: 1px solid #ececec; margin: 34px 0; }
  table { width: 100%%; border-collapse: collapse; margin: 18px 0; font-size: 14px; }
  th, td { text-align: left; padding: 9px 12px; border: 1px solid #e8e8e8; vertical-align: top; }
  th { background: #fafafa; font-weight: 600; color: #333; }
  tr:nth-child(even) td { background: #fcfcfc; }
  em { color: #777; }
  @media (max-width: 640px) { .page { padding: 26px 20px 40px; } h1 { font-size: 26px; } }
</style>
</head>
<body>
<div class="page">
%(body)s
</div>
</body>
</html>
"""


if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    md = io.open(src, encoding='utf-8').read()
    html = TEMPLATE % {'title': 'No Original Trains', 'body': convert(md)}
    io.open(dst, 'w', encoding='utf-8', newline='\n').write(html)
    print('wrote', dst, len(html))
