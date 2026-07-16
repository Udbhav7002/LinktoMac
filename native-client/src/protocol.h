#pragma once

#include <stdint.h>
#include <string>

// Event Types
constexpr uint8_t EVENT_TYPE_TOUCH = 0x01;
constexpr uint8_t EVENT_TYPE_KEY = 0x02;
constexpr uint8_t EVENT_TYPE_SCROLL = 0x03;
// Server -> Client events
constexpr uint8_t EVENT_TYPE_FORMAT_CHANGE = 0x04; // SPS/PPS changed

// Endianness helpers (Big Endian over wire)
inline void write_be32(uint8_t* buf, uint32_t val) {
    buf[0] = (val >> 24) & 0xFF;
    buf[1] = (val >> 16) & 0xFF;
    buf[2] = (val >> 8) & 0xFF;
    buf[3] = val & 0xFF;
}

inline void write_be_float(uint8_t* buf, float val) {
    union { float f; uint32_t i; } u;
    u.f = val;
    write_be32(buf, u.i);
}

inline uint32_t read_be32(const uint8_t* buf) {
    return (buf[0] << 24) | (buf[1] << 16) | (buf[2] << 8) | buf[3];
}

inline float read_be_float(const uint8_t* buf) {
    union { float f; uint32_t i; } u;
    u.i = read_be32(buf);
    return u.f;
}
