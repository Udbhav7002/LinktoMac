#include "mirror_client.h"
#include <iostream>
#include <string>

using namespace std;

int main(int argc, char* argv[]) {
    string host = "127.0.0.1";
    int video_port = 27189;

    bool connect_mode = false;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "--host" && i + 1 < argc) {
            host = argv[++i];
        } else if (arg == "--video-port" && i + 1 < argc) {
            video_port = stoi(argv[++i]);
        } else if (arg == "--connect" && i + 1 < argc) {
            host = argv[++i];
            connect_mode = true;
        } else if (arg == "--help") {
            cout << "Usage: " << argv[0] << " [--host IP] [--video-port PORT] [--connect HOST]\n";
            return 0;
        }
    }

    MirrorClient client(host, video_port);
    if (connect_mode) client.set_connect_mode(true);
    if (!client.start()) {
        cerr << "Client failed to start\n";
        return 1;
    }

    return 0;
}
