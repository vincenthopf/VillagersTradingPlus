package com.lion.villagertradingplus.platform;


import java.nio.file.Path;

public class ConfigDirectory {

    public static Path getConfigDirectory() {
        return com.lion.villagertradingplus.platform.fabric.ConfigDirectoryImpl.getConfigDirectory();
    }
}
