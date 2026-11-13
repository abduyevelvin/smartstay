"use client";

import { useEffect, useRef, useState } from "react";
import { Bot, Minimize2, Send, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useChat, useChatHistory } from "@/lib/hooks/useAI";
import { useAuthStore } from "@/lib/auth";
import type { MessageDto } from "@/types";

// ── Per-user, per-browser-tab session ─────────────────────────────────────
// Scoped to the authenticated user so switching accounts never leaks one
// user's chat history into another user's widget.
function getOrCreateSessionId(userId: string): string {
    const key = `smartstay_chat_session_${userId}`;
    let id = sessionStorage.getItem(key);
    if (!id) {
        id = crypto.randomUUID();
        sessionStorage.setItem(key, id);
    }
    return id;
}

// ── Single message bubble ──────────────────────────────────────────────────
function MessageBubble({msg}: { msg: MessageDto }) {
    // Server history returns lowercase "user"/"assistant" (Spring AI MessageType.getValue());
    // locally-created optimistic messages use uppercase "USER". Normalise here.
    const isUser = msg.role.toUpperCase() === "USER";
    return (
        <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
            {!isUser && (
                <div className="mr-2 mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10">
                    <Bot className="h-3.5 w-3.5 text-primary"/>
                </div>
            )}
            <div
                className={`max-w-[80%] rounded-2xl px-3.5 py-2 text-sm leading-relaxed whitespace-pre-wrap ${
                    isUser
                        ? "bg-primary text-primary-foreground rounded-br-sm"
                        : "bg-muted text-foreground rounded-bl-sm"
                }`}
            >
                {msg.content}
            </div>
        </div>
    );
}

// ── Typing indicator ───────────────────────────────────────────────────────
function TypingIndicator() {
    return (
        <div className="flex justify-start">
            <div className="mr-2 mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10">
                <Bot className="h-3.5 w-3.5 text-primary"/>
            </div>
            <div className="rounded-2xl rounded-bl-sm bg-muted px-4 py-3">
                <span className="flex gap-1">
                    {[0, 1, 2].map((i) => (
                        <span
                            key={i}
                            className="h-1.5 w-1.5 rounded-full bg-muted-foreground animate-bounce"
                            style={{animationDelay: `${i * 150}ms`}}
                        />
                    ))}
                </span>
            </div>
        </div>
    );
}

// ── Main widget ────────────────────────────────────────────────────────────
export function ChatWidget() {
    const {isAuthenticated, user} = useAuthStore();
    const isManager = user?.role === "HOTEL_MANAGER";
    const [open, setOpen] = useState(false);
    // sessionId is derived from the authenticated user — starts empty until the
    // user identity is known so we never send requests without a valid session.
    const [sessionId, setSessionId] = useState<string>("");
    const [messages, setMessages] = useState<MessageDto[]>([]);
    const [input, setInput] = useState("");
    const bottomRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);

    // When the authenticated user changes (login / logout / account switch):
    //  1. Generate (or retrieve) a session ID scoped to that user.
    //  2. Clear in-memory messages so a previous user's chat is never shown.
    useEffect(() => {
        if (user?.id) {
            setSessionId(getOrCreateSessionId(user.id));
        } else {
            setSessionId("");
        }
        setMessages([]);
    }, [user?.id]); // eslint-disable-line react-hooks/exhaustive-deps

    const {data: history, isLoading: historyLoading} = useChatHistory(
        open ? sessionId : ""
    );
    const {mutate: sendMessage, isPending} = useChat();

    // Populate from server history on first open
    useEffect(() => {
        if (history && messages.length === 0) {
            setMessages(history);
        }
    }, [history]); // eslint-disable-line react-hooks/exhaustive-deps

    // Scroll to bottom whenever messages change
    useEffect(() => {
        bottomRef.current?.scrollIntoView({behavior: "smooth"});
    }, [messages, isPending]);

    // Focus input when panel opens
    useEffect(() => {
        if (open) setTimeout(() => inputRef.current?.focus(), 50);
    }, [open]);

    const handleSend = () => {
        const text = input.trim();
        if (!text || isPending) return;

        const userMsg: MessageDto = {role: "USER", content: text};
        setMessages((prev) => [...prev, userMsg]);
        setInput("");

        sendMessage(
            {sessionId, message: text},
            {
                onSuccess: (data) => {
                    setMessages((prev) => [
                        ...prev,
                        {role: "ASSISTANT", content: data.reply},
                    ]);
                },
                onError: () => {
                    setMessages((prev) => [
                        ...prev,
                        {
                            role: "ASSISTANT",
                            content: "Sorry, something went wrong. Please try again.",
                        },
                    ]);
                },
            }
        );
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    // Don't render for unauthenticated users
    if (!isAuthenticated) return null;

    return (
        <>
            {/* ── Backdrop (mobile) ───────────────────────── */}
            {open && (
                <div
                    className="fixed inset-0 z-40 bg-black/20 backdrop-blur-sm sm:hidden"
                    onClick={() => setOpen(false)}
                />
            )}

            {/* ── Slide-in panel ──────────────────────────── */}
            <div
                className={`fixed bottom-0 right-0 z-50 flex flex-col bg-background border-l border-t shadow-2xl
                    transition-all duration-300 ease-in-out
                    w-full sm:w-[380px] sm:rounded-tl-2xl
                    ${open ? "h-[520px] sm:h-[560px]" : "h-0 overflow-hidden border-0"}`}
            >
                {open && (
                    <>
                        {/* Header */}
                        <div className="flex items-center justify-between border-b px-4 py-3 shrink-0">
                            <div className="flex items-center gap-2">
                                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10">
                                    <Bot className="h-4 w-4 text-primary"/>
                                </div>
                                <div>
                                    <p className="text-sm font-semibold leading-none">SmartStay AI</p>
                                    <p className="mt-0.5 text-xs text-muted-foreground">
                                        {isManager ? "Hotel assistant & manager tools" : "Ask me anything about hotels"}
                                    </p>
                                </div>
                            </div>
                            <button
                                onClick={() => setOpen(false)}
                                className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                            >
                                <Minimize2 className="h-4 w-4"/>
                            </button>
                        </div>

                        {/* Messages */}
                        <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
                            {historyLoading && messages.length === 0 && (
                                <div className="flex justify-center py-6">
                                    <span className="text-xs text-muted-foreground">Loading history…</span>
                                </div>
                            )}

                            {!historyLoading && messages.length === 0 && (
                                <div className="flex flex-col items-center justify-center py-8 text-center gap-2">
                                    <div
                                        className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
                                        <Bot className="h-6 w-6 text-primary"/>
                                    </div>
                                    <p className="text-sm font-medium">Hi! I&apos;m your SmartStay assistant.</p>
                                    {isManager ? (
                                        <p className="text-xs text-muted-foreground max-w-[260px]">
                                            Ask me about hotels, or say{" "}
                                            <span
                                                className="font-medium text-foreground">&ldquo;create a hotel&rdquo;</span>{" "}
                                            to start the AI hotel creation wizard. 🏨
                                        </p>
                                    ) : (
                                        <p className="text-xs text-muted-foreground max-w-[260px]">
                                            Ask me to recommend hotels, check availability, or anything
                                            else about your stay.
                                        </p>
                                    )}
                                </div>
                            )}

                            {messages.map((msg, i) => (
                                <MessageBubble key={i} msg={msg}/>
                            ))}

                            {isPending && <TypingIndicator/>}
                            <div ref={bottomRef}/>
                        </div>

                        {/* Input */}
                        <div className="border-t px-3 py-3 shrink-0">
                            <div className="flex items-end gap-2 rounded-xl border bg-muted/40 px-3 py-2">
                                <textarea
                                    ref={inputRef}
                                    rows={1}
                                    value={input}
                                    onChange={(e) => setInput(e.target.value)}
                                    onKeyDown={handleKeyDown}
                                    placeholder={isManager ? "Ask about hotels, or 'create a hotel'…" : "Ask about hotels, cities, amenities…"}
                                    disabled={isPending}
                                    className="flex-1 resize-none bg-transparent text-sm outline-none placeholder:text-muted-foreground disabled:opacity-50 max-h-28"
                                    style={{minHeight: "1.5rem"}}
                                />
                                <Button
                                    size="icon"
                                    className="h-7 w-7 shrink-0 rounded-lg"
                                    disabled={!input.trim() || isPending}
                                    onClick={handleSend}
                                >
                                    <Send className="h-3.5 w-3.5"/>
                                </Button>
                            </div>
                            <p className="mt-1.5 text-center text-[10px] text-muted-foreground">
                                Enter to send · Shift+Enter for new line
                            </p>
                        </div>
                    </>
                )}
            </div>

            {/* ── Floating button ─────────────────────────── */}
            <button
                onClick={() => setOpen((v) => !v)}
                aria-label={open ? "Close AI chat" : "Open AI chat"}
                className={`fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full shadow-lg
                    bg-primary text-primary-foreground transition-all duration-200 hover:scale-105 active:scale-95
                    ${open ? "rotate-90 opacity-0 pointer-events-none" : "rotate-0 opacity-100"}`}
            >
                <Bot className="h-6 w-6"/>
            </button>

            {/* ── Close button (visible when open, above panel) ── */}
            {open && (
                <button
                    onClick={() => setOpen(false)}
                    aria-label="Close AI chat"
                    className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full shadow-lg
                        bg-primary text-primary-foreground transition-all duration-200 hover:scale-105 active:scale-95"
                >
                    <X className="h-6 w-6"/>
                </button>
            )}
        </>
    );
}
