cmake_minimum_required(VERSION 3.18.1)

project(operaproxy_tun2proxy)

add_library(
    native-lib
    SHARED
    tun2proxy_jni.cpp)

find_library(
    log-lib
    log)

target_link_libraries(
    native-lib
    ${log-lib})
