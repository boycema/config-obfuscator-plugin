# Config Obfuscator Maven Plugin

一个用于在Maven构建过程中对配置文件中的敏感信息进行混淆的Maven插件。该插件支持多种配置文件格式，包括Properties、YAML、JSON和XML文件。

## 功能特性

- 支持对Properties、YAML、JSON和XML配置文件中的指定键值进行混淆
- 可配置需要混淆的键名列表
- 可配置包含和排除的文件模式
- 支持自定义混淆值的前缀和后缀
- 在Maven生命周期的prepare-package阶段自动执行

## 支持的文件格式

- `.properties` - Properties配置文件
- `.yml` / `.yaml` - YAML配置文件
- `.json` - JSON配置文件
- `.xml` - XML配置文件

## 安装

此插件可通过Maven中央仓库获取。在你的项目中添加以下依赖：

```xml
<plugin>
    <groupId>com.boyce.plugin</groupId>
    <artifactId>config-obfuscator-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</plugin>
```

## 使用方法

### 基本配置

在你的`pom.xml`中添加插件配置：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.boyce.plugin</groupId>
            <artifactId>config-obfuscator-maven-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>obfuscate</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <keys>
                    <key>db.password</key>
                    <key>secret.key</key>
                    <key>api.token</key>
                </keys>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 高级配置

```xml
<plugin>
    <groupId>com.boyce.plugin</groupId>
    <artifactId>config-obfuscator-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>obfuscate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- 要混淆的键列表 -->
        <keys>
            <key>db.password</key>
            <key>secret.key</key>
            <key>nested.secret</key>
        </keys>
        
        <!-- 包含的文件模式 -->
        <includes>
            <include>**/*.properties</include>
            <include>**/*.yml</include>
            <include>**/*.yaml</include>
            <include>**/*.json</include>
            <include>**/*.xml</include>
        </includes>
        
        <!-- 排除的文件模式 -->
        <excludes>
            <exclude>**/logback.xml</exclude>
            <exclude>**/application-test.*</exclude>
        </excludes>
        
        <!-- 自定义混淆值的前缀和后缀 -->
        <prefix>ENC(</prefix>
        <suffix>)</suffix>
    </configuration>
</plugin>
```

## 配置参数

| 参数 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `keys` | List | - | 需要混淆的配置键列表 |
| `includes` | List | `**/*.properties`, `**/*.yml`, `**/*.yaml`, `**/*.json`, `**/*.xml` | 要处理的文件模式列表 |
| `excludes` | List | - | 要排除的文件模式列表 |
| `prefix` | String | `-` | 混淆值的前缀 |
| `suffix` | String | `-` | 混淆值的后缀 |

## 示例

假设你有以下配置文件：

### application.properties
```properties
db.username=admin
db.password=secret_password
other.config=value
```

### bootstrap.yml
```yaml
server:
  port: 8080
secret:
  key: my_super_secret_key
nested:
  secret: nested_value
  public: visible
```

### application.json
```json
{
  "server": {
    "port": 8080
  },
  "db": {
    "password": "secret_password"
  },
  "other": "value"
}
```

### application.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <db>
        <password>secret_password</password>
    </db>
    <nested>
        <secret>nested_value</secret>
    </nested>
    <public>visible</public>
</configuration>
```

如果你配置了以下键进行混淆：
- `db.password`
- `secret.key`
- `nested.secret`

那么在运行`mvn prepare-package`或`mvn package`后，这些文件将被修改为：

### application.properties (混淆后)
```properties
db.username=admin
db.password=-OBFUSCATED-
other.config=value
```

### bootstrap.yml (混淆后)
```yaml
server:
  port: 8080
secret:
  key: -OBFUSCATED-
nested:
  secret: -OBFUSCATED-
  public: visible
```

### application.json (混淆后)
```json
{
  "server" : {
    "port" : 8080
  },
  "db" : {
    "password" : "-OBFUSCATED-"
  },
  "other" : "value"
}
```

### application.xml (混淆后)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <db>
        <password>-OBFUSCATED-</password>
    </db>
    <nested>
        <secret>-OBFUSCATED-</secret>
    </nested>
    <public>visible</public>
</configuration>
```

## 执行命令

直接执行混淆操作：
```bash
mvn com.boyce.plugin:config-obfuscator-maven-plugin:obfuscate
```

通常情况下，插件会在`prepare-package`阶段自动执行，因此你只需运行：
```bash
mvn package
```

## 构建插件

如果你想自己构建插件，请确保已安装Maven 3.6+，然后执行：

```bash
cd config-obfuscator-plugin
mvn clean install
```

## 注意事项

1. 插件会直接修改目标目录中的配置文件，建议在版本控制系统中忽略这些修改。
2. 确保正确配置了需要混淆的键名，避免遗漏敏感信息。
3. 混淆操作是不可逆的，如需恢复原始值，请使用版本控制系统的备份。
4. 插件默认在`prepare-package`阶段执行，这意味着混淆将在打包之前完成。

## 许可证

MIT License