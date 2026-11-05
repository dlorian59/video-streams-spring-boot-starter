#!/usr/bin/env python3
"""EN: Verify a disposable TLS consumer using synthetic media.
RU: Проверка отдельного TLS-потребителя на синтетическом видео.
"""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request
import uuid

base = os.environ.get("VIDEO_TEST_URL", "https://127.0.0.1:19445")
source = Path(os.environ["VIDEO_TEST_SOURCE"])
# EN: Trust the supplied test certificate; do not disable TLS verification.
# RU: Доверяем переданному тестовому сертификату; проверка TLS не отключается.
context = ssl.create_default_context(cafile=os.environ["VIDEO_TEST_CERTIFICATE"])
client = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),
    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(method, path, body=None, headers=None, expected=200):
    operation = urllib.request.Request(base + path, data=body, headers=headers or {}, method=method)
    try:
        response = client.open(operation, timeout=200)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        data = response.read()
        assert response.status == expected, (method, path, response.status, data[:300])
        return data, response.headers

# EN: This small synthetic test file is buffered by the test client. Browser verification covers streaming upload separately.
# RU: Небольшой синтетический файл буферизуется тестовым клиентом. Потоковую загрузку отдельно проверяет браузер.
source_id = "demo:" + str(uuid.uuid4())
encoded = urllib.parse.quote(source_id, safe="")
request("GET", "/api/videos/" + encoded + "/assets/" + str(uuid.uuid4()) + "/playlist.m3u8", expected=401)
session = json.loads(request("GET", "/api/session")[0])
request("POST", "/login", urllib.parse.urlencode({"username": "demo", "password": os.environ["DEMO_PASSWORD"]}).encode(),
        {"Content-Type": "application/x-www-form-urlencoded", session["csrfHeader"]: session["csrfToken"]}, expected=204)
session = json.loads(request("GET", "/api/session")[0])
assert session["username"] == "demo"
csrf = {session["csrfHeader"]: session["csrfToken"]}
prepare_body = json.dumps({"sourceKey": source_id}).encode()
request("POST", "/api/videos/" + encoded + "/prepare", prepare_body, {"Content-Type": "application/json"}, expected=403)
request("PUT", "/api/video-sources/other%3Aprivate", b"not uploaded", {**csrf, "Content-Type": "video/mp4"}, expected=403)
request("GET", "/api/videos/other%3Aprivate/assets/" + str(uuid.uuid4()) + "/playlist.m3u8", expected=403)
request("POST", "/api/videos/" + encoded + "/prepare", b'{"sourceKey":"other:private"}',
        {**csrf, "Content-Type": "application/json"}, expected=400)
request("GET", "/objects/" + encoded, expected=404)
commit = json.loads(request("PUT", "/api/video-sources/" + encoded, source.read_bytes(),
        {**csrf, "Content-Type": "video/mp4"}, expected=201)[0])
assert commit["committed"] and commit["key"] == source_id and commit["bytes"] == source.stat().st_size
prepared = json.loads(request("POST", "/api/videos/" + encoded + "/prepare", prepare_body,
        {**csrf, "Content-Type": "application/json"}, expected=201)[0])
assert prepared["status"] == "ready" and prepared["durationSeconds"] >= 30 and prepared["segmentCount"] >= 2
playlist, headers = request("GET", prepared["playlistUrl"])
assert "application/vnd.apple.mpegurl" in headers["Content-Type"]
text = playlist.decode()
assert "#EXTM3U" in text and "#EXT-X-ENDLIST" in text and 'URI="init.mp4"' in text
segments = re.findall(r"^segment-\d{6}\.m4s$", text, re.M)
assert len(segments) == prepared["segmentCount"]
prefix = prepared["playlistUrl"].rsplit("/", 1)[0] + "/"
initialization, headers = request("GET", prefix + "init.mp4")
assert "video/mp4" in headers["Content-Type"] and b"ftyp" in initialization[:64]
segment, headers = request("GET", prefix + segments[0])
assert "video/mp4" in headers["Content-Type"] and len(segment) > 100
partial, headers = request("GET", prefix + segments[0], headers={"Range": "bytes=0-63"}, expected=206)
assert len(partial) == 64 and headers["Content-Range"].startswith("bytes 0-63/")
request("GET", prefix + "not-an-artifact.txt", expected=400)
print(json.dumps({"result": "PASS", "sourceBytes": commit["bytes"], "sourceId": source_id, "prepared": prepared,
      "checks": ["anonymous401", "csrf403", "foreignSource403", "foreignAsset403", "bodyMismatch400", "genericRoute404",
      "uploadCommit201", "prepare201", "manifest", "init", "segment", "range206", "allowlist400"]}, indent=2))
