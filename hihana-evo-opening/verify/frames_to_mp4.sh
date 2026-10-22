#!/usr/bin/env bash
# Encode a directory of frame_NNNNN.png files (any start number) to H.264 / yuv420p / 60 fps MP4.
# Usage: frames_to_mp4.sh DIR OUT.mp4 [FPS=60] [CRF=16]
set -euo pipefail
if [[ $# -lt 2 ]]; then echo "usage: $0 DIR OUT.mp4 [FPS=60] [CRF=16]" >&2; exit 2; fi
DIR=$1; OUT=$2; FPS=${3:-60}; CRF=${4:-16}
shopt -s nullglob; files=("$DIR"/frame_*.png); shopt -u nullglob
[[ ${#files[@]} -gt 0 ]] || { echo "no frame_*.png in $DIR" >&2; exit 1; }
# glob input sorts lexicographically -> zero-padded names keep frame order; odd sizes are padded to even for 4:2:0.
ffmpeg -hide_banner -loglevel error -y \
  -framerate "$FPS" -pattern_type glob -i "$DIR/frame_*.png" \
  -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2,scale=out_color_matrix=bt709:out_range=tv,format=yuv420p" \
  -c:v libx264 -preset medium -crf "$CRF" -pix_fmt yuv420p -r "$FPS" \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv \
  -movflags +faststart "$OUT"
echo "wrote $OUT (${#files[@]} frames @ ${FPS} fps)"
