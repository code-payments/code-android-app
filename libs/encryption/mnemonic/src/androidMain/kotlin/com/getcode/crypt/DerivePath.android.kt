package com.getcode.crypt

import com.getcode.model.Domain

fun DerivePath.Companion.relationship(domain: Domain): DerivePath {
    return DerivePath.newInstance("m/44'/501'/0'/0'/0'/0", password = domain.relationshipHost)!!
}
