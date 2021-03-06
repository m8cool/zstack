package org.zstack.storage.ceph.primary

import org.zstack.header.errorcode.ErrorCode
import org.zstack.storage.ceph.primary.CephPrimaryStoragePoolInventory

doc {

	title "在这里输入结构的名称"

	ref {
		name "error"
		path "org.zstack.storage.ceph.primary.APIQueryCephPrimaryStoragePoolReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "0.6"
		clz ErrorCode.class
	}
	ref {
		name "inventories"
		path "org.zstack.storage.ceph.primary.APIQueryCephPrimaryStoragePoolReply.inventories"
		desc "null"
		type "List"
		since "0.6"
		clz CephPrimaryStoragePoolInventory.class
	}
}
