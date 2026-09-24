"""Download pinned official KWS binaries; no Baidu credentials or runtime network needed."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
MODEL = 'sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01-mobile'
VERSION = '1.13.8'

def download(url, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        part = path.with_suffix(path.suffix + '.part')
        subprocess.run(['curl', '-fL', '--retry', '2', url, '-o', str(part)], check=True)
        part.replace(path)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    aar = ROOT / f'app/libs/sherpa-onnx-{VERSION}.aar'
    archive = ROOT / 'downloads' / f'{MODEL}.tar.bz2'
    download(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/{aar.name}', aar)
    download(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/{archive.name}', archive)
    lock = json.loads((ROOT / 'assets.lock.json').read_text(encoding='utf-8'))
    for key, path in [('sdk', aar), ('model', archive)]:
        if sha(path) != lock[key]['sha256']:
            raise RuntimeError(f'Checksum mismatch: {path}; remove the incomplete download and retry')
    target = ROOT / 'app/src/main/assets/kws'
    target.mkdir(parents=True, exist_ok=True)
    files = {
        'encoder.onnx': 'encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx',
        'decoder.onnx': 'decoder-epoch-12-avg-2-chunk-16-left-64.onnx',
        'joiner.onnx': 'joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx',
        'tokens.txt': 'tokens.txt', 'MODEL_README.md': 'README.md',
    }
    with tarfile.open(archive) as tar:
        for dest, source in files.items():
            with tar.extractfile(f'{MODEL}/{source}') as src, (target / dest).open('wb') as dst:
                shutil.copyfileobj(src, dst)
    tokens = {line.split()[0] for line in (target / 'tokens.txt').read_text(encoding='utf-8').splitlines()}
    for name in ['keywords.txt', 'keywords-prefixed.txt']:
        lines = (target.parent / name).read_text(encoding='utf-8').splitlines()
        assert len(lines) == 8
        for line in lines:
            parts = line.split()
            assert parts[-1].startswith('@'), line
            assert all(token in tokens for token in parts[:-1]), f'Unknown phoneme: {line}'
    print('Verified SDK, model checksums and both keyword vocabularies.')

if __name__ == '__main__':
    main()
