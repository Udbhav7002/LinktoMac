#include <pybind11/pybind11.h>
#include "mirror_client.h"

namespace py = pybind11;
using namespace std;

PYBIND11_MODULE(mirror_core, m) {
    m.doc() = "LinktoMac native mirror client";

    py::class_<MirrorClient>(m, "MirrorClient")
        .def(py::init<const string&, int>(),
             py::arg("host") = "127.0.0.1",
             py::arg("video_port") = 27189)
        .def("set_connect_mode", &MirrorClient::set_connect_mode)
        .def("start", &MirrorClient::start, py::call_guard<py::gil_scoped_release>())
        .def("stop", &MirrorClient::stop)
        .def("is_running", &MirrorClient::is_running);
}
