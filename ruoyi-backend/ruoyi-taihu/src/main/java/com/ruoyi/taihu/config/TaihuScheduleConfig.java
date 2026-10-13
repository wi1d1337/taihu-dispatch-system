package com.ruoyi.taihu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * taihu 模块调度配置：开启 @Scheduled（超时督办计时器）
 *
 * 若依基座默认未开 @EnableScheduling（定时任务走 quartz 控制台），
 * 督办是代码内固定节奏的计时器（每 10 分钟），不走 quartz，故在模块内开启。
 *
 * @author taihu
 */
@Configuration
@EnableScheduling
public class TaihuScheduleConfig
{
}
