# README: 中文

![](docs\image\ic_launcher.png)

### 项目描述

**What is KShare?**
​	KShare 是一个能在局域网内和其它支持 SMB 协议的设备，进行一键文件共享的 Android App。
​	功能类似于 ES 管理器 中的局域网共享功能。扫描局域网内的 SMB 服务端，把文件复制到服务端可读写的目录下。仅支持把文件从 Android 设备分享到其它支持 SMBv1 协议的设备，当前版本(v0.0.1)也仅支持分享到开放匿名读写权限的目录。

**Features**
​	支持 SMBv1 
​	支持 Android 4.2+

### 如何使用

**Supported Operating Systems**
​	Android 4.2+
**How to use it ?**
​	在其它的 App 的  "分享" 选择 KShare ，等待扫描完成，打开共享设备，长按共享目录，即可共享文件到该目录下。


### 如何编译

**Required**
​        [jcifs](https://www.jcifs.org/)

**Building**
​	git clone https://github.com/k2archer/kshare.git
​	use Android Studio open it .
​	build it.

### 版本更新

**Change Log**

v0.0.1 (2018/12/27)

* 支持可匿名读写共享