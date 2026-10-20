import os

def app(environ, start_response):
    path = environ.get('PATH_INFO', '/').lstrip('/')

    if path in ('LinkToMac.apk', 'downloads/LinkToMac.apk'):
        apk_candidates = [
            os.path.join(os.path.dirname(__file__), 'downloads', 'LinkToMac.apk'),
            os.path.join(os.path.dirname(__file__), 'public', 'LinkToMac.apk'),
            os.path.join(os.path.dirname(__file__), 'LinkToMac.apk')
        ]
        apk_path = next((p for p in apk_candidates if os.path.exists(p)), None)
        if apk_path:
            size = os.path.getsize(apk_path)
            start_response('200 OK', [
                ('Content-Type', 'application/vnd.android.package-archive'),
                ('Content-Disposition', 'attachment; filename="LinkToMac.apk"'),
                ('Content-Length', str(size))
            ])
            with open(apk_path, 'rb') as f:
                return [f.read()]

    html_candidates = [
        os.path.join(os.path.dirname(__file__), 'index.html'),
        os.path.join(os.path.dirname(__file__), 'public', 'index.html')
    ]
    html_path = next((p for p in html_candidates if os.path.exists(p)), None)
    if html_path:
        with open(html_path, 'rb') as f:
            content = f.read()
        start_response('200 OK', [
            ('Content-Type', 'text/html; charset=utf-8'),
            ('Content-Length', str(len(content)))
        ])
        return [content]

    start_response('404 Not Found', [('Content-Type', 'text/plain')])
    return [b'Not Found']
