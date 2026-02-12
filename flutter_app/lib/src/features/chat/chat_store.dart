import 'chat_store_contract.dart';
import 'chat_store_stub.dart' if (dart.library.io) 'chat_store_mobile.dart';

Future<ChatStore> openChatStore() => openChatStoreImpl();
