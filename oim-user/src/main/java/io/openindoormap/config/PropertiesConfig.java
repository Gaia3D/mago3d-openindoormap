package io.openindoormap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import lombok.Data;

@Data
@Configuration
@PropertySource("classpath:openindoormap.properties")
@ConfigurationProperties(prefix = "openindoormap")
public class PropertiesConfig {

	private String osType;
	private boolean callRemoteEnable;
	private String serverIp;
	private String serverPort;
	private String restAuthKey;
	
	// User excel batch registration
	private String uploadData;
	private String userConverterDir;
	
}