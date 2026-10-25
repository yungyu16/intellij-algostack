package com.wenjunhuang.algostack.runtime

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.wenjunhuang.algostack.domain.CodeDojo
import com.wenjunhuang.algostack.model.Constants

/** Keep the existing PasswordSafe service names so no credential migration is needed. */
object CredentialsStore {
    private fun attributes(dojo: CodeDojo) = CredentialAttributes(generateServiceName(Constants.PROJECT_ID, dojo.value))
    fun read(dojo: CodeDojo): String? = PasswordSafe.instance.getPassword(attributes(dojo))
    fun write(dojo: CodeDojo, value: String?) = PasswordSafe.instance.setPassword(attributes(dojo), value)
}
