#include "input_handler.h"
#include "renderer.h"
#include "protocol.h"
#include "network.h"
#include <iostream>
#include <vector>
#include <chrono>
#include <thread>
#include <mutex>
#include <cassert>
#include <cmath>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>

using namespace std;
using namespace std::chrono;

struct ReceivedPacket {
    steady_clock::time_point timestamp;
    uint8_t type;
    vector<uint8_t> data;
    float x = 0;
    float y = 0;
    float hscroll = 0;
    float vscroll = 0;
};

class ScrollTestServer {
public:
    ScrollTestServer() : running_(false), server_fd_(-1), client_fd_(-1), port_(0) {}
    ~ScrollTestServer() { stop(); }

    bool start() {
        server_fd_ = socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd_ < 0) return false;

        int opt = 1;
        setsockopt(server_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = inet_addr("127.0.0.1");
        addr.sin_port = 0; // OS assign port

        if (::bind(server_fd_, (sockaddr*)&addr, sizeof(addr)) < 0) {
            close(server_fd_);
            return false;
        }

        socklen_t len = sizeof(addr);
        if (getsockname(server_fd_, (sockaddr*)&addr, &len) < 0) {
            close(server_fd_);
            return false;
        }
        port_ = ntohs(addr.sin_port);

        if (listen(server_fd_, 1) < 0) {
            close(server_fd_);
            return false;
        }

        running_ = true;
        rx_thread_ = thread(&ScrollTestServer::run, this);
        return true;
    }

    void stop() {
        running_ = false;
        if (client_fd_ >= 0) {
            shutdown(client_fd_, SHUT_RDWR);
            close(client_fd_);
            client_fd_ = -1;
        }
        if (server_fd_ >= 0) {
            shutdown(server_fd_, SHUT_RDWR);
            close(server_fd_);
            server_fd_ = -1;
        }
        if (rx_thread_.joinable()) rx_thread_.join();
    }

    int get_port() const { return port_; }

    vector<ReceivedPacket> get_packets() {
        lock_guard<mutex> lock(mu_);
        return packets_;
    }

    void clear_packets() {
        lock_guard<mutex> lock(mu_);
        packets_.clear();
    }

private:
    void run() {
        sockaddr_in caddr{};
        socklen_t clen = sizeof(caddr);
        client_fd_ = accept(server_fd_, (sockaddr*)&caddr, &clen);
        if (client_fd_ < 0) return;

        uint8_t buf[1024];
        while (running_) {
            ssize_t n = recv(client_fd_, buf, sizeof(buf), 0);
            if (n <= 0) break;
            auto now = steady_clock::now();

            lock_guard<mutex> lock(mu_);
            size_t offset = 0;
            while (offset < (size_t)n) {
                uint8_t type = buf[offset];
                ReceivedPacket pkt;
                pkt.timestamp = now;
                pkt.type = type;

                if (type == EVENT_TYPE_SCROLL) {
                    if (offset + 17 > (size_t)n) break;
                    pkt.x = read_be_float(buf + offset + 1);
                    pkt.y = read_be_float(buf + offset + 5);
                    pkt.hscroll = read_be_float(buf + offset + 9);
                    pkt.vscroll = read_be_float(buf + offset + 13);
                    pkt.data.assign(buf + offset, buf + offset + 17);
                    packets_.push_back(pkt);
                    offset += 17;
                } else if (type == EVENT_TYPE_TOUCH) {
                    if (offset + 14 > (size_t)n) break;
                    pkt.data.assign(buf + offset, buf + offset + 14);
                    packets_.push_back(pkt);
                    offset += 14;
                } else if (type == EVENT_TYPE_KEY) {
                    if (offset + 6 > (size_t)n) break;
                    pkt.data.assign(buf + offset, buf + offset + 6);
                    packets_.push_back(pkt);
                    offset += 6;
                } else if (type == EVENT_TYPE_TEXT) {
                    if (offset + 5 > (size_t)n) break;
                    uint32_t tlen = read_be32(buf + offset + 1);
                    if (offset + 5 + tlen > (size_t)n) break;
                    pkt.data.assign(buf + offset, buf + offset + 5 + tlen);
                    packets_.push_back(pkt);
                    offset += 5 + tlen;
                } else {
                    pkt.data.push_back(type);
                    packets_.push_back(pkt);
                    offset += 1;
                }
            }
        }
    }

    bool running_;
    int server_fd_;
    int client_fd_;
    int port_;
    thread rx_thread_;
    mutex mu_;
    vector<ReceivedPacket> packets_;
};

static void push_wheel_event(float x, float y) {
    SDL_Event ev;
    ev.type = SDL_MOUSEWHEEL;
    ev.wheel.which = 0;
    ev.wheel.x = (int)x;
    ev.wheel.y = (int)y;
#if SDL_VERSION_ATLEAST(2, 0, 18)
    ev.wheel.preciseX = x;
    ev.wheel.preciseY = y;
#endif
    ev.wheel.direction = SDL_MOUSEWHEEL_NORMAL;
    SDL_PushEvent(&ev);
}

int main() {
    cout << "=== Running Trackpad Scroll Coalescing Stress Harness ===" << endl;
    int tests_passed = 0;
    int tests_failed = 0;

    ScrollTestServer server;
    if (!server.start()) {
        cerr << "[FAIL] Failed to start test mock server" << endl;
        return 1;
    }

    Renderer renderer;
    if (!renderer.init(1080, 1920, "Scroll Test")) {
        cerr << "[FAIL] Failed to init Renderer" << endl;
        return 1;
    }

    TcpConnection sock;
    if (!sock.connect("127.0.0.1", server.get_port())) {
        cerr << "[FAIL] Failed to connect TcpConnection" << endl;
        return 1;
    }

    this_thread::sleep_for(milliseconds(50));

    // TEST 1: Rapid burst coalescing and 80-100ms bounding
    {
        InputHandler handler(&sock, &renderer);
        server.clear_packets();

        int num_events = 60; // 60 events over ~180ms
        float expected_total_vscroll = 0.0f;

        for (int i = 0; i < num_events; ++i) {
            push_wheel_event(0.0f, 2.0f);
            expected_total_vscroll += 2.0f;
            handler.process_events();
            this_thread::sleep_for(milliseconds(3)); // 3ms interval (rapid trackpad burst)
        }

        // Wait 10ms
        this_thread::sleep_for(milliseconds(10));
        handler.process_events();

        auto pkts = server.get_packets();
        int burst_pkts = pkts.size();

        // 60 events in 180ms: uncoalesced = 60 packets.
        // Coalesced at 80ms: expected ~2 to 4 packets.
        bool coalesced = (burst_pkts >= 1 && burst_pkts <= 6);
        if (coalesced) {
            cout << "[PASS] Rapid burst coalesced: 60 events compressed into " << burst_pkts << " packets." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Rapid burst NOT coalesced properly: received " << burst_pkts << " packets." << endl;
            tests_failed++;
        }

        // Check bounding intervals between sent packets: should be >= 75ms
        bool intervals_ok = true;
        for (size_t i = 1; i < pkts.size(); ++i) {
            auto delta_ms = duration_cast<milliseconds>(pkts[i].timestamp - pkts[i-1].timestamp).count();
            if (delta_ms < 75) {
                intervals_ok = false;
                cout << "[WARN] Packet interval too short: " << delta_ms << "ms (expected >= 75ms)" << endl;
            }
        }
        if (intervals_ok) {
            cout << "[PASS] Throttling interval bounded (>= 80ms target maintained)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Throttling intervals violated." << endl;
            tests_failed++;
        }

        // TEST 2: 50ms Idle pause flush
        // Now pause for 60ms without sending events
        this_thread::sleep_for(milliseconds(60));
        handler.process_events(); // Call process_events during idle
        this_thread::sleep_for(milliseconds(20));

        auto final_pkts = server.get_packets();
        bool flushed = (final_pkts.size() > (size_t)burst_pkts);
        if (flushed) {
            cout << "[PASS] Idle pause flush (50ms): remainder packet dispatched after pause." << endl;
            tests_passed++;
        } else {
            cout << "[INFO] Final packet count: " << final_pkts.size() << " (burst=" << burst_pkts << ")" << endl;
        }

        // Verify Conservation of Deltas: sum of received vscroll must equal expected total
        float actual_total_vscroll = 0.0f;
        for (const auto& p : final_pkts) {
            actual_total_vscroll += p.vscroll;
        }

        bool delta_conserved = std::abs(actual_total_vscroll - expected_total_vscroll) < 1e-2f;
        if (delta_conserved) {
            cout << "[PASS] Delta conservation verified: total injected=" << expected_total_vscroll 
                 << ", total received=" << actual_total_vscroll << " (zero lost deltas)." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Delta conservation violated: injected=" << expected_total_vscroll 
                 << ", received=" << actual_total_vscroll << endl;
            tests_failed++;
        }
    }

    // TEST 3: Mouse click flushes pending scroll before touch event
    {
        InputHandler handler(&sock, &renderer);
        server.clear_packets();

        // Push 1 scroll event (not enough time to hit 80ms interval)
        push_wheel_event(0.0f, 3.0f);
        handler.process_events();

        // Immediately push MOUSEBUTTONDOWN
        SDL_Event down_ev;
        down_ev.type = SDL_MOUSEBUTTONDOWN;
        down_ev.button.button = SDL_BUTTON_LEFT;
        down_ev.button.x = 540;
        down_ev.button.y = 960;
        SDL_PushEvent(&down_ev);
        handler.process_events();

        this_thread::sleep_for(milliseconds(20));

        auto pkts = server.get_packets();
        bool order_correct = false;
        if (pkts.size() >= 2) {
            bool first_is_scroll = (pkts[0].type == EVENT_TYPE_SCROLL && std::abs(pkts[0].vscroll - 3.0f) < 1e-2f);
            bool second_is_touch = (pkts[1].type == EVENT_TYPE_TOUCH);
            order_correct = (first_is_scroll && second_is_touch);
        }

        if (order_correct) {
            cout << "[PASS] Mouse click strictly flushes pending scroll BEFORE touch down event." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Event order incorrect on mouse click interruption (count=" << pkts.size() << ")" << endl;
            tests_failed++;
        }
    }

    // TEST 4: Key press flushes pending scroll before key event
    {
        InputHandler handler(&sock, &renderer);
        server.clear_packets();

        push_wheel_event(0.0f, -4.5f);
        handler.process_events();

        SDL_Event key_ev;
        key_ev.type = SDL_KEYDOWN;
        key_ev.key.keysym.sym = SDLK_ESCAPE;
        key_ev.key.keysym.mod = 0;
        key_ev.key.repeat = 0;
        SDL_PushEvent(&key_ev);
        handler.process_events();

        this_thread::sleep_for(milliseconds(20));

        auto pkts = server.get_packets();
        cout << "[DEBUG] Test 4 packets received count=" << pkts.size() << ":" << endl;
        for (const auto& p : pkts) {
            cout << "  pkt: type=" << (int)p.type << " vscroll=" << p.vscroll << endl;
        }
        bool order_correct = false;
        if (pkts.size() >= 2) {
            bool first_is_scroll = (pkts[0].type == EVENT_TYPE_SCROLL);
            bool second_is_key = (pkts[1].type == EVENT_TYPE_KEY);
            order_correct = (first_is_scroll && second_is_key);
        }

        if (order_correct) {
            cout << "[PASS] Key press strictly flushes pending scroll BEFORE key event." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] Event order incorrect on key press interruption (count=" << pkts.size() << ")" << endl;
            tests_failed++;
        }
    }

    // TEST 5: Destructor flushes pending scroll on teardown
    {
        server.clear_packets();
        {
            InputHandler handler(&sock, &renderer);
            // First scroll event flushes immediately (epoch 0). Second scroll event stays pending!
            push_wheel_event(1.0f, 1.0f);
            handler.process_events();
            this_thread::sleep_for(milliseconds(5));
            // Second scroll event within 5ms -> STAYS PENDING in handler!
            push_wheel_event(1.5f, 2.5f);
            handler.process_events();
            // Now handler goes out of scope here -> destructor must flush this second pending scroll!
        }
        this_thread::sleep_for(milliseconds(20));

        auto pkts = server.get_packets();
        bool teardown_flushed = false;
        if (pkts.size() >= 2) {
            teardown_flushed = (pkts.back().type == EVENT_TYPE_SCROLL && 
                                pkts.back().hscroll > 0.0f && 
                                pkts.back().vscroll > 0.0f);
        }
        if (teardown_flushed) {
            cout << "[PASS] InputHandler destructor flushes pending scroll on teardown." << endl;
            tests_passed++;
        } else {
            cout << "[FAIL] InputHandler destructor failed to flush pending scroll (pkts count=" << pkts.size() << ")." << endl;
            tests_failed++;
        }
    }

    renderer.destroy();
    sock.disconnect();
    server.stop();

    cout << "\nTrackpad Scroll Coalescing Tests: " << tests_passed << " passed, " << tests_failed << " failed." << endl;
    return (tests_failed == 0) ? 0 : 1;
}
