Android GNSS  APP—— 完整实现方案
基于附件需求文档，以下是可直接编译运行的完整 Android APP 实现（Kotlin + AndroidX + OSMDroid 开源地图）。
涵盖：GNSS 原始观测采集、伪距解算、星历解析与卫星位置计算、WLS 加权最小二乘定位、方位角卫星分类、室内 TDOA 差分定位、地图轨迹与设置管理。

一、项目结构与配置
app/src/main/

├── AndroidManifest.xml  
├── java/com/gnss/locator/    
│  	   ├── MainActivity.kt            # 室外定位模式      
│ 	   ├── IndoorActivity.kt          # 室内定位模式（TDOA）    
│ 	   ├── MapViewActivity.kt         # 地图显示      
│  	   ├── SettingsActivity.kt        # 参数设置    
│  	   ├── gnss/    
│      │     ├── GnssService.kt         # GNSS采集（位置+原始观测+星历）    
│      │     ├── PseudorangeCalculator.kt    
│      │     ├── NavigationMessageParser.kt  # GPS星历解析    
│      │     └── SatellitePosition.kt   # 开普勒轨道计算    
│      ├── solver/     
│      │     ├── WlsSolver.kt           # WLS定位解算    
│      │     └── TdoaSolver.kt          # 室内伪距差定位（数值优化）    
│      └── util/    
│            ├── CoordinateUtils.kt     # ECEF/LLH/ENU转换、方位角俯仰角     
│            └── Prefs.kt               # 配置存储    
└── res/layout/ (activity_main.xml 等)

编译与运行说明
步骤
操作

1    Android Studio → New Project → Empty Activity（包名 com.gnss.locator），替换上述文件

2    同步 Gradle，连接手机（需支持 GNSS Raw Measurement：Android 7.0+，如小米/华为/三星/Pixel）

3 必须    手机「开发者选项 → 启用 GNSS 原始测量」开关打开

4    首次运行授权位置权限，置于开阔室外，约 30s 收星后 WLS 解算输出定位及误差

5    地图页支持滑动缩放、轨迹弱化显示、PI 点连线测距


文件清单
.github/workflows/build.yml          ★ CI自动编译配置   
settings.gradle                      ★   
build.gradle                         ★   
gradle.properties                    ★   
app/build.gradle                     ★   
app/src/main/AndroidManifest.xml     ★（已修正）  
app/src/main/res/values/strings.xml  ★  
app/src/main/res/values/themes.xml   ★  
app/src/main/res/layout/activity_main.xml      ★  
app/src/main/res/layout/activity_indoor.xml    ★  
app/src/main/res/layout/activity_settings.xml  ★  
app/src/main/java/com/gnss/locator/LocatorApp.kt           ★  
app/src/main/java/com/gnss/locator/MainActivity.kt         ★（已修正）  
app/src/main/java/com/gnss/locator/IndoorActivity.kt       ★（含绘图View）  
app/src/main/java/com/gnss/locator/MapViewActivity.kt      ★（完整版）  
app/src/main/java/com/gnss/locator/SettingsActivity.kt     ★  
app/src/main/java/com/gnss/locator/gnss/GnssService.kt     ★（已修正）  
app/src/main/java/com/gnss/locator/gnss/PseudorangeCalculator.kt ★  
app/src/main/java/com/gnss/locator/gnss/NavigationMessageParser.kt ★（重写）  
app/src/main/java/com/gnss/locator/gnss/SatellitePosition.kt ★  
app/src/main/java/com/gnss/locator/solver/WlsSolver.kt        ◎ 沿用之前回复  
app/src/main/java/com/gnss/locator/solver/TdoaSolver.kt       ◎ 沿用之前回复  
app/src/main/java/com/gnss/locator/util/CoordinateUtils.kt    ◎ 沿用之前回复  
app/src/main/java/com/gnss/locator/util/Prefs.kt              ★（已修正）  
