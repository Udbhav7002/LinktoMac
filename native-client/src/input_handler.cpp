#include "input_handler.h"
#include "protocol.h"
#include <iostream>
#include <vector>
#include <cstring>
#include <chrono>
#include <cmath>

using namespace std;

InputHandler::InputHandler(TcpConnection* control_sock, Renderer* renderer)
    : control_sock_(control_sock),
      renderer_(renderer),
      is_dragging_(false),
      has_pending_scroll_(false),
      pending_scroll_x_(0.0f),
      pending_scroll_y_(0.0f),
      pending_hscroll_(0.0f),
      pending_vscroll_(0.0f),
      last_scroll_sent_time_{},
      last_scroll_event_time_{} {
    SDL_StartTextInput();
}

InputHandler::~InputHandler() {
    flush_scroll();
    SDL_StopTextInput();
}

uint32_t InputHandler::map_sdl_to_android_keycode(SDL_Keycode sym) {
    switch (sym) {
        case SDLK_ESCAPE:    return 4;   // KEYCODE_BACK
        case SDLK_BACKSPACE: return 67;  // KEYCODE_DEL
        case SDLK_DELETE:    return 112; // KEYCODE_FORWARD_DEL
        case SDLK_RETURN:
        case SDLK_KP_ENTER:  return 66;  // KEYCODE_ENTER
        case SDLK_TAB:       return 61;  // KEYCODE_TAB
        case SDLK_UP:        return 19;  // KEYCODE_DPAD_UP
        case SDLK_DOWN:      return 20;  // KEYCODE_DPAD_DOWN
        case SDLK_LEFT:      return 21;  // KEYCODE_DPAD_LEFT
        case SDLK_RIGHT:     return 22;  // KEYCODE_DPAD_RIGHT
        case SDLK_HOME:      return 122; // KEYCODE_MOVE_HOME
        case SDLK_END:       return 123; // KEYCODE_MOVE_END
        case SDLK_PAGEUP:    return 92;  // KEYCODE_PAGE_UP
        case SDLK_PAGEDOWN:  return 93;  // KEYCODE_PAGE_DOWN
        default:             return 0;   // Printable characters handled via SDL_TEXTINPUT
    }
}

bool InputHandler::process_events() {
    SDL_Event event;
    bool pending_motion = false;
    float pending_motion_x = 0;
    float pending_motion_y = 0;

    auto flush_motion = [&]() {
        if (pending_motion) {
            flush_scroll();
            send_touch(2, pending_motion_x, pending_motion_y, 1.0f); // 2=MOVE
            pending_motion = false;
        }
    };

    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT:
                flush_motion();
                flush_scroll();
                return false;

            case SDL_MOUSEBUTTONDOWN:
            case SDL_MOUSEBUTTONUP: {
                if (event.button.button != SDL_BUTTON_LEFT) break;
                
                flush_motion(); // Send any pending movement before the click/release
                if (event.type == SDL_MOUSEBUTTONDOWN) {
                    flush_scroll(); // Flush pending scroll before touch down
                }
                
                float dev_x, dev_y;
                renderer_->window_to_device(event.button.x, event.button.y, dev_x, dev_y);
                
                uint8_t action = (event.type == SDL_MOUSEBUTTONDOWN) ? 0 : 1; // 0=DOWN, 1=UP
                send_touch(action, dev_x, dev_y, 1.0f);
                
                is_dragging_ = (event.type == SDL_MOUSEBUTTONDOWN);
                
                // Track last sent coordinates to implement a deadzone
                last_sent_x_ = event.button.x;
                last_sent_y_ = event.button.y;
                break;
            }

            case SDL_MOUSEMOTION: {
                if (is_dragging_) {
                    // Deadzone filter: ignore microscopic movements (e.g. from Mac trackpads)
                    int dx = event.motion.x - last_sent_x_;
                    int dy = event.motion.y - last_sent_y_;
                    if (dx * dx + dy * dy >= 4) { // Move at least 2 pixels
                        renderer_->window_to_device(event.motion.x, event.motion.y, pending_motion_x, pending_motion_y);
                        pending_motion = true;
                        last_sent_x_ = event.motion.x;
                        last_sent_y_ = event.motion.y;
                    }
                }
                break;
            }

            case SDL_MOUSEWHEEL: {
                flush_motion();
                int mouse_x, mouse_y;
                if (is_dragging_) {
                    mouse_x = last_sent_x_;
                    mouse_y = last_sent_y_;
                } else {
                    SDL_GetMouseState(&mouse_x, &mouse_y);
                }
                float dev_x, dev_y;
                renderer_->window_to_device(mouse_x, mouse_y, dev_x, dev_y);
                
#if SDL_VERSION_ATLEAST(2, 0, 18)
                float hscroll = (event.wheel.preciseX != 0.0f) ? event.wheel.preciseX : static_cast<float>(event.wheel.x);
                float vscroll = (event.wheel.preciseY != 0.0f) ? event.wheel.preciseY : static_cast<float>(event.wheel.y);
#else
                float hscroll = static_cast<float>(event.wheel.x);
                float vscroll = static_cast<float>(event.wheel.y);
#endif
                if (event.wheel.direction == SDL_MOUSEWHEEL_FLIPPED) {
                    hscroll *= -1.0f;
                    vscroll *= -1.0f;
                }

                pending_scroll_x_ = dev_x;
                pending_scroll_y_ = dev_y;
                pending_hscroll_ += hscroll;
                pending_vscroll_ += vscroll;
                has_pending_scroll_ = true;

                auto now = std::chrono::steady_clock::now();
                last_scroll_event_time_ = now;
                if (std::chrono::duration_cast<std::chrono::milliseconds>(now - last_scroll_sent_time_).count() >= 80) {
                    flush_scroll();
                }
                break;
            }

            case SDL_TEXTINPUT: {
                // Ignore text events if Cmd (KMOD_GUI) or Ctrl is held so shortcuts don't inject text
                if ((SDL_GetModState() & (KMOD_GUI | KMOD_CTRL)) != 0) {
                    break;
                }
                flush_motion();
                flush_scroll();
                send_text(event.text.text);
                break;
            }

            case SDL_KEYDOWN:
            case SDL_KEYUP: {
                SDL_Keycode sym = event.key.keysym.sym;
                uint16_t mod = event.key.keysym.mod;

                // Intercept macOS Command (Cmd) system shortcuts
                if ((mod & KMOD_GUI) != 0) {
                    if (event.type == SDL_KEYDOWN && !event.key.repeat) {
                        flush_motion();
                        flush_scroll();
                        switch (sym) {
                            case SDLK_h:
                                send_key(0, 3);   // KEYCODE_HOME (Cmd+H)
                                break;
                            case SDLK_b:
                                send_key(0, 4);   // KEYCODE_BACK (Cmd+B)
                                break;
                            case SDLK_TAB:
                            case SDLK_s:
                                send_key(0, 187); // KEYCODE_APP_SWITCH / Recents (Cmd+Tab / Cmd+S)
                                break;
                            case SDLK_p:
                                send_key(0, 26);  // KEYCODE_POWER / Lock (Cmd+P)
                                break;
                            case SDLK_n:
                                send_key(0, 83);  // KEYCODE_NOTIFICATION (Cmd+N)
                                break;
                            case SDLK_v:
                                if (SDL_HasClipboardText()) {
                                    char* clip = SDL_GetClipboardText();
                                    if (clip) {
                                        send_text(clip);
                                        SDL_free(clip);
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                }

                // Navigation & action keys only (printable characters are handled via SDL_TEXTINPUT)
                uint32_t android_keycode = map_sdl_to_android_keycode(sym);
                if (android_keycode != 0) {
                    flush_motion();
                    flush_scroll();
                    if (event.key.repeat) {
                        if (event.type == SDL_KEYDOWN) {
                            send_key(0, android_keycode);
                        }
                    } else {
                        uint8_t action = (event.type == SDL_KEYDOWN) ? 0 : 1;
                        send_key(action, android_keycode);
                    }
                }
                break;
            }
        }
    }
    
    flush_motion();

    // Idle flush: ensure final scroll deltas are dispatched after gesture cessation
    if (has_pending_scroll_) {
        auto now = std::chrono::steady_clock::now();
        auto since_sent = std::chrono::duration_cast<std::chrono::milliseconds>(now - last_scroll_sent_time_).count();
        auto since_event = std::chrono::duration_cast<std::chrono::milliseconds>(now - last_scroll_event_time_).count();
        if (since_sent >= 80 || since_event >= 50) {
            flush_scroll();
        }
    }

    return true;
}

void InputHandler::send_touch(uint8_t action, float x, float y, float pressure) {
    uint8_t buf[14];
    buf[0] = EVENT_TYPE_TOUCH;
    buf[1] = action;
    write_be_float(buf + 2, x);
    write_be_float(buf + 6, y);
    write_be_float(buf + 10, pressure);
    control_sock_->send_all(buf, sizeof(buf));
}

void InputHandler::send_key(uint8_t action, uint32_t keycode) {
    uint8_t buf[6];
    buf[0] = EVENT_TYPE_KEY;
    buf[1] = action;
    write_be32(buf + 2, keycode);
    control_sock_->send_all(buf, sizeof(buf));
}

void InputHandler::flush_scroll() {
    if (!has_pending_scroll_) {
        return;
    }
    if (std::abs(pending_hscroll_) > 1e-4f || std::abs(pending_vscroll_) > 1e-4f) {
        send_scroll(pending_scroll_x_, pending_scroll_y_, pending_hscroll_, pending_vscroll_);
        last_scroll_sent_time_ = std::chrono::steady_clock::now();
    }
    pending_hscroll_ = 0.0f;
    pending_vscroll_ = 0.0f;
    has_pending_scroll_ = false;
}

void InputHandler::send_scroll(float x, float y, float hscroll, float vscroll) {
    uint8_t buf[17];
    buf[0] = EVENT_TYPE_SCROLL;
    write_be_float(buf + 1, x);
    write_be_float(buf + 5, y);
    write_be_float(buf + 9, hscroll);
    write_be_float(buf + 13, vscroll);
    control_sock_->send_all(buf, sizeof(buf));
}

void InputHandler::send_text(const string& text) {
    if (text.empty() || text.size() > 4096) return;
    uint32_t len = static_cast<uint32_t>(text.size());
    vector<uint8_t> buf(5 + len);
    buf[0] = EVENT_TYPE_TEXT;
    write_be32(buf.data() + 1, len);
    memcpy(buf.data() + 5, text.data(), len);
    control_sock_->send_all(buf.data(), buf.size());
}
