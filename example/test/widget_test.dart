import 'package:flutter_test/flutter_test.dart';

import 'package:wifi_connector_example/main.dart';

void main() {
  testWidgets('Verify App', (WidgetTester tester) async {
    await tester.pumpWidget(MyApp());
  });
}
