import { ArrowRight, PhoneCall, Video } from 'lucide-react';
import { NoticeBanner } from './Common';

export function CallsScreen({
  isOnline,
  onBackToConversations,
}: {
  isOnline: boolean;
  onBackToConversations: () => void;
}) {
  return (
    <div className="h-full overflow-y-auto bg-[var(--chatbox-bg)] px-5 py-6 flex flex-col items-center">
      <div className="w-full max-w-[760px] space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
              STAY CLOSE, FROM ANYWHERE
            </p>
            <h1 className="font-display text-2xl font-bold text-[var(--chatbox-ink)]">
              Calls
            </h1>
            <p className="text-xs text-[#8f89a3]">
              Some conversations deserve to be heard.
            </p>
          </div>
          <div className="w-11 h-11 rounded-2xl bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shrink-0">
            <PhoneCall className="w-5 h-5" />
          </div>
        </div>
        <div className="border-b border-[var(--chatbox-border)]" />
        {!isOnline && (
          <NoticeBanner
            isError
            text="Offline · Voice and video calls are disabled without internet. Return to Messages for offline-queued chatting."
          />
        )}
      </div>

      <div className="my-auto py-10 max-w-[420px] flex flex-col items-center text-center space-y-3">
        <div className="relative w-40 h-40 flex items-center justify-center">
          <div className="absolute inset-0 rounded-full border-2 border-[#9b86d0]/30 animate-pulse" />
          <div className="absolute inset-5 rounded-full border-2 border-[#5b4a8f]/20" />
          <div className="w-16 h-16 rounded-full bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shadow-xs">
            <Video className="w-7 h-7" />
          </div>
          <span className="absolute top-3 right-5 text-[#5b4a8f] text-sm">✳</span>
          <span className="absolute bottom-4 left-5 text-[#9b86d0] text-sm">✦</span>
        </div>

        <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
          A LITTLE SOMETHING TO LOOK FORWARD TO
        </p>
        <h2 className="font-display text-2xl font-bold text-[var(--chatbox-ink)]">
          Closer than a message.
        </h2>
        <p className="text-sm text-[#6b657d] leading-relaxed">
          Voice and video calling are planned, but not connected yet. Once calling services are configured, this space can bring you face to face.
        </p>
        <button
          type="button"
          onClick={onBackToConversations}
          className="mt-2 inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] text-white text-sm font-semibold transition-colors cursor-pointer"
        >
          Back to conversations
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
