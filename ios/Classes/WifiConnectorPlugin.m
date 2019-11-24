#import "WifiConnectorPlugin.h"
#import <wifi_connector/wifi_connector-Swift.h>

@implementation WifiConnectorPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftWifiConnectorPlugin registerWithRegistrar:registrar];
}
@end
