import { Mic, Pause, Play, Square, Trash2, X } from 'lucide-react';
import { formatDurationMs } from '../services/chatMediaService';

const WAVE_BARS = [6, 12, 18, 9, 15, 20, 11, 16, 8, 14, 19, 10, 15, 7, 13, 17, 9, 12];

export function VoiceNoteBubblePlayer({
  messageId,
  audioUrl,
  durationMs,
  isOwn,
  playingId,
  isPlaying,
  progressFraction,
  onTogglePlay,
}: {
  messageId: string;
  audioUrl: string;
  durationMs?: number | null;
  isOwn: boolean;
  playingId: string | null;
  isPlaying: boolean;
  progressFraction: number;
  onTogglePlay: (id: string, url: string) => void;
}) {
  const active = playingId === messageId;
  const filledCount = active ? Math.round(progressFraction * WAVE_BARS.length) : 0;

  return (
    <div className="flex items-center gap-2.5 px-3 py-2 min-w-[210px]">
      <button
        type="button"
        onClick={() => onTogglePlay(messageId, audioUrl)}
        aria-label={active && isPlaying ? 'Pause voice note' : 'Play voice note'}
        className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 transition-colors cursor-pointer ${
          isOwn
            ? 'bg-white/20 hover:bg-white/30 text-white'
            : 'bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f]'
        }`}
      >
        {active && isPlaying ? (
          <Pause className="w-4 h-4" />
        ) : (
          <Play className="w-4 h-4 ml-0.5" />
        )}
      </button>

      <div className="flex items-center gap-0.5 flex-1 h-6">
        {WAVE_BARS.map((h, i) => (
          <span
            key={i}
            style={{ height: `${h}px` }}
            className={`w-1 rounded-full transition-colors ${
              i < filledCount
                ? isOwn
                  ? 'bg-white'
                  : 'bg-[#5b4a8f]'
                : isOwn
                  ? 'bg-white/40'
                  : 'bg-[#5b4a8f]/25'
            }`}
          />
        ))}
      </div>

      <span
        className={`text-[11px] font-medium tabular-nums shrink-0 ${
          isOwn ? 'text-white/85' : 'text-[#6b657d]'
        }`}
      >
        {formatDurationMs(durationMs)}
      </span>
    </div>
  );
}

export function VoiceRecordingBar({
  elapsedMs,
  onCancel,
  onStopAndPreview,
}: {
  elapsedMs: number;
  onCancel: () => void;
  onStopAndPreview: () => void;
}) {
  return (
    <div className="flex items-center gap-2.5 px-3 py-2 rounded-2xl bg-[#fff1ed] border border-[#f4c7bc]">
      <span className="w-2.5 h-2.5 rounded-full bg-[#dc2626] animate-pulse shrink-0" />
      <Mic className="w-4 h-4 text-[#9b5145] shrink-0" />
      <span className="text-xs font-semibold text-[#9b5145] tabular-nums flex-1">
        Recording voice note · {formatDurationMs(elapsedMs)} / 1:00
      </span>
      <button
        type="button"
        onClick={onCancel}
        aria-label="Cancel recording"
        className="w-8 h-8 rounded-lg hover:bg-white/70 text-[#9b5145] flex items-center justify-center cursor-pointer"
      >
        <X className="w-4 h-4" />
      </button>
      <button
        type="button"
        onClick={onStopAndPreview}
        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[#9b5145] text-white text-xs font-semibold cursor-pointer"
      >
        <Square className="w-3 h-3" />
        Stop
      </button>
    </div>
  );
}

export function PendingVoicePreviewBar({
  durationMs,
  isPlaying,
  onTogglePreview,
  onRemove,
}: {
  durationMs: number;
  isPlaying: boolean;
  onTogglePreview: () => void;
  onRemove: () => void;
}) {
  return (
    <div className="flex items-center gap-2.5 px-3 py-2 rounded-2xl bg-[#f3eff8] border border-[var(--chatbox-border)]">
      <button
        type="button"
        onClick={onTogglePreview}
        aria-label={isPlaying ? 'Pause preview' : 'Play preview'}
        className="w-8 h-8 rounded-full bg-[#6250a0] text-white flex items-center justify-center shrink-0 cursor-pointer"
      >
        {isPlaying ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5 ml-0.5" />}
      </button>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold text-[#2a2340]">Voice note ready</p>
        <p className="text-[11px] text-[#6b657d] tabular-nums">{formatDurationMs(durationMs)}</p>
      </div>
      <button
        type="button"
        onClick={onRemove}
        aria-label="Remove voice note"
        className="w-8 h-8 rounded-lg hover:bg-[#fff1ed] text-[#9b5145] flex items-center justify-center cursor-pointer"
      >
        <Trash2 className="w-4 h-4" />
      </button>
    </div>
  );
}
