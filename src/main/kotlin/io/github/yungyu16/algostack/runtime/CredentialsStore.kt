package io.github.yungyu16.algostack.runtime

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.model.Constants

/** Keep the existing PasswordSafe service names so no credential migration is needed. */
object CredentialsStore {
    private fun attributes(dojo: CodeDojo) = CredentialAttributes(generateServiceName(Constants.PROJECT_ID, dojo.value))
    fun read(dojo: CodeDojo): String? = PasswordSafe.instance.getPassword(attributes(dojo))
    fun write(dojo: CodeDojo, value: String?) = PasswordSafe.instance.setPassword(attributes(dojo), value)
}
