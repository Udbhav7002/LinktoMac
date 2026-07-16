#pragma once

#include <string>
#include <stdint.h>

class TcpConnection {
public:
    TcpConnection();
    ~TcpConnection();

    bool listen_and_accept(int port);
    bool connect(const std::string& host, int port);
    void disconnect();
    void stop_listening();
    
    // Blocking read of exact bytes
    bool recv_exact(uint8_t* buf, int len);
    
    // Non-blocking read, returns bytes read or < 0 on error
    int recv(uint8_t* buf, int len);
    
    // Blocking write of all bytes
    bool send_all(const uint8_t* buf, int len);

private:
    void apply_socket_options();

    int sock_fd_;
    int server_fd_ = -1;
};
