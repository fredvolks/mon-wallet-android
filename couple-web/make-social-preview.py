"""Publish the approved public rendez-vous artwork without redrawing an older theme.
Re-author with social-preview.html and render-social-preview.cjs. No personal data.
"""
from pathlib import Path
import shutil
import struct
root = Path(__file__).resolve().parent / 'assets'
for source, output in [('loveroll-messenger-og-v139.png','loveroll-invite-og.png'),
                       ('loveroll-rendezvous-scene-v139.png','loveroll-premium-scene.png')]:
    asset = root / source
    header = asset.read_bytes()[:24]
    assert header[:8] == b'\x89PNG\r\n\x1a\n', source
    assert struct.unpack('>II', header[16:24]) == (1200,630), source
    assert asset.stat().st_size > 30000, source
    shutil.copyfile(asset, root / output)
    print('LoveRoll approved artwork:', output, asset.stat().st_size, 'bytes')
