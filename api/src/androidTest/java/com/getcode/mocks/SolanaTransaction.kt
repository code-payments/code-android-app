package com.getcode.mocks

import com.getcode.network.repository.decodeBase64
import com.getcode.solana.SolanaTransaction

object SolanaTransaction {
    /// Mock Timelock Create Account Transaction
    ///
    /// - Instruction 1: Advance Nonce (No Change)
    ///
    /// - Instruction 2: Initialize Timelock
    ///   - Accounts
    ///     - Timelock: 4B6DvoEJugGBrKedasVvT2n5GykbtsVUFknsz12FWEv9
    ///     - Vault: EQDNoJMxbAWr81XFM1TykpFuJuK5CjxmJLyP45S95wR8
    ///     - VaultOwner: G9zksyBhzGzFDPjfF333HEXCWKstU8Go4JvUChBNBLf7
    ///     - Mint: kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6
    ///     - Time Authority: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///     - Payer: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///
    /// - Instruction 3: Activate Timelock
    ///   - Arguments
    ///     - Timelock Bump: 254
    ///     - Unlock Duration: 1209600
    ///   - Accounts
    ///     - Timelock: 4B6DvoEJugGBrKedasVvT2n5GykbtsVUFknsz12FWEv9
    ///     - Time Authority: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///     - Vault Owner: G9zksyBhzGzFDPjfF333HEXCWKstU8Go4JvUChBNBLf7
    ///     - Payer: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///
    fun mockTimelockCreateAccount(): Pair<SolanaTransaction, String> =
        transaction("AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAHCwksDha4qmHvDLlGQXd2cjb/PDR7UoWkLijNmnwnO1nuN8DXVqgdQXIsX+LDS9MUe8jvjg/Ff6Vj6VZaNxer98K8yIAO97UcHAd2xm4zfG8AtfQDMyT8/7QC0Sen9vq/lgSaWxmcYQR245MI8/QpznC4B3qZptwxn5SBWI9Bizr8PKg85sywGzAYembl67Ega1GZSC7hiY7u3Yz/akHwTAoGp9UXGSxWjuCKhF9z0peIzwNcMUWyGrNE2AYuqUAAAAan1RcZLFxRIYzJTD1K8X9Y2u4Im6H9ROPb2YoAAAAACzM4oKssyEHVsBS8ajz3VikYdLMZyVF9m7+p5OlmHvkG3fbh12Whk9nL4UbO63msHLSF7V9bN5E6jPWFfv8AqQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADT/Zs1d0xV/ZGv3T91QbiBCGO0SfEPaaw23Wl4xfukuo9TwTAtFbaDGnTWWclU2+wNsZXm1C/+ztSBss80USmAIJAwEFAAQEAAAACgoJAgMEBwAACAkGEK+vbR8NmJvtgK8bAAAAAAA=")


    /// Mock Timelock Transfer Transaction
    ///
    /// - Instruction 1: Advance Nonce (No Change)
    ///
    /// - Instruction 2: Memo (No Change)
    ///
    /// - Instruction 3: Transfer With Authority
    ///   - Arguments
    ///     - Timelock Bump: 254
    ///     - Amount: 100,000
    ///   - Accounts
    ///     - Timelock: 4B6DvoEJugGBrKedasVvT2n5GykbtsVUFknsz12FWEv9
    ///     - Vault: EQDNoJMxbAWr81XFM1TykpFuJuK5CjxmJLyP45S95wR8
    ///     - Vault Owner: G9zksyBhzGzFDPjfF333HEXCWKstU8Go4JvUChBNBLf7
    ///     - Time Authority: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///     - Destination: 67ziVAtk8djEKbwNtFhUPrkiEi8RdYaq4GXkpHzHd2Nq
    ///     - Payer: codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR
    ///
    fun mockTimelockTransfer(): Pair<SolanaTransaction, String> =
        transaction("AwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMBBQsJLA4WuKph7wy5RkF3dnI2/zw0e1KFpC4ozZp8JztZ7iQYzVNCBjclXJYZnnW/qqMMrZYZW3kw0tUfkXGZME3W8YCgxDEPphRY3pwZfCU0DX/cOINdTIAmgAV/PDU7Sb28wo0yvPsf8LaCBUwJ3RnR+3aSgzBoJHAiecmPvEso33XZUdKV1xqVpjXEvAK7jYk6Km7icyk6zdOxaeZ2BcGFq+8rrjypbi2C47Bf1CUp8twlL490HlWcy+LfNleQo38G3fbh12Whk9nL4UbO63msHLSF7V9bN5E6jPWFfv8AqQan1RcZLFaO4IqEX3PSl4jPA1wxRbIas0TYBi6pQAAADT/Zs1d0xV/ZGv3T91QbiBCGO0SfEPaaw23Wl4xfuksAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL07vagml0PRaRpURdBuPBkcUZpFmJ/+gGl99Fn5HlwmLKzhK8DCQMBBwAEBAAAAAoALFpUQUVBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE9CAgFAwIABAAGCRFEgN7AgUVHpfughgEAAAAAAA==")


    /// Mock SPL Transfer Transaction
    ///
    /// - Instruction 1: Advance Nonce
    ///
    /// - Instruction 2: Memo
    ///
    /// - Instruction 3: Token Transfer
    ///   - Arguments:
    ///     - Amount: 100,000
    ///   - Accounts:
    ///     - Source: HFMeSarShcvgKARwwMTS6WafuRzhW1BPRsWzo3WEa4FS
    ///     - Destination: FmoyfcoDYya27XtJcZUKtnXRn6RKNA8yrApkf88DcKvj
    ///
    fun mockSimpleTransfer(): Pair<SolanaTransaction, String> =
        transaction(
            "A1tAcqFQvsLAwkzzR6IyioVR7RanubupIBmSTJLmVRgehHpCXA4vw1iydd/nXGRM2MFkcOO486sPb" +
                    "Y/t5YkcoQ+HczaWQofwtUOGMXOaitdnW4QV2IrNouP7OekZ5X/nrVTaMNsRldU4hDKv4TpBW5ZtUu" +
                    "MxNj+K0hYaiOhe0bwAe03tCXMy5w2tn22FQRD98vyOk9lllvhOiNvrz2MkQBrKyGZkvFx+GxzIr2J" +
                    "jKp2ZcLiYQAabpKmmqae3WbejDAMBBAkJLA4WuKph7wy5RkF3dnI2/zw0e1KFpC4ozZp8JztZ7gu8" +
                    "C2nMEidsVQv64veR8KGN+uSVB8t3QXls8pS1g3hpYe7aeLzQPu4s5+1/zFV3d0sA+QjBW17I1Gz2k" +
                    "SOyqk7bfkolDNcGZ49pe8RELDOdW6UGljTmveo3XvKO0SSYDPFod1RaA9JNk/lmcAnsZEkxtqcuUg" +
                    "nkYIlTqbFPislJBqfVFxksVo7gioRfc9KXiM8DXDFFshqzRNgGLqlAAAAFSlNQ+F3IgtYUpVZyeIo" +
                    "pbd8eq6vQpgZ4iEky9O72oAbd9uHXZaGT2cvhRs7reawctIXtX1s3kTqM9YV+/wCpAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB9c5VBOZKF0Rkn4tPQFamDoUjF8oa04karh4ZnmDORpgMIA" +
                    "wEFAAQEAAAABgAsWlRBRUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQT0HAw" +
                    "QDAgkDoIYBAAAAAAA="
        )


    fun mockCloseDormantAccount(): Pair<SolanaTransaction, String> =
        transaction("Ag83S4kpkDdVCkjHoQMbY1PFlHQuN4iNc0HvKRY4GAYtS2fJg2sD0pTswcXX80wQ0l1LNz" +
                "fEPMFq1L65zh3ZqAMalMR4K8eVSOb2026zqdo2y+jtsqXZUwLXo5u7kpxcQdesl0RyvYFW5TtfOahNZEmt" +
                "130Bqr7JW52XByUWOMUFAgEFCwksDha4qmHvDLlGQXd2cjb/PDR7UoWkLijNmnwnO1nuymNnIuP7iSQN87" +
                "RAiXXExCM/rcMSDk1ufucz1QBgrMsQi2SY08yHh0QVR56YXP/W3hU8rVeYaNQ3Pou62KeUaMXihwH1Rl/j" +
                "ONnj6X35HTZ5IaCcgRGCf5ejrg2iOClK2CjKDyXQ3p6u/9OZeYr/MDJt3dfzYPPo50L/Nl89J5fiWbl2YM" +
                "re3ew8o0M5ZWp069OdcB1BTXvdFHPVb8kEggan1RcZLFaO4IqEX3PSl4jPA1wxRbIas0TYBi6pQAAABt32" +
                "4ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "VKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL07vagDT/Zr02LPzD6xb5Nf2x4+R0n7wWJyKAfah6AyAlW" +
                "XFdklqEX6a61ZsH0qvimrsORM6Wqr1q/dAdLEVNifIlQSgYIAwIGAAQEAAAACQAsWlRBRUFBQUFBQUFBQU" +
                "FBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQT0KBgQDAAAHCAnltTryqwjJkP8KAwQBAAkscCGscRyO" +
                "Df8KBwQDAQUABwgJtxJGnJRtoSL/CgYEAwAABwgJq95e6SL6ygH/")

    fun mockCloseDormantAccount2(): Pair<SolanaTransaction, String> =
        transaction("AkH0mv3Wb+chdEx7SDmLKBDu+MEJuAecPUhgHUV2uHPx6rF061sMmL055hGCGrW8iGnv4" +
                "GPR7ozqV74Xc2g2cwHYt8SiG9X1Jyesj7INStyZ+5yY3OtB12RnOUEhcmGbju91ehu/W/O22Kz5kdpb7v" +
                "kWzNEQIRiWopVHJy2IdFoKAgEFCwksDha4qmHvDLlGQXd2cjb/PDR7UoWkLijNmnwnO1nuKAvMOr2G9++" +
                "j3FKHaYR45PV5PjUE7fNwU+O8l4ozKdELvAfBis4EiZ742UqJJmdRJAxuh5nsIdVfzf6/BQOMlkwWp+FX" +
                "GEbr7ILKWA/w3YDZHN+ZR0WEK3Sf0BzuIUH2qEDphy9YNl6UtOVMq/2wT3QVO5YV9DY27Nq4MO3E+QnVQ" +
                "/teS/yrCfsfzeH4V559kxgBVPs4O/8VZrj607/XuAan1RcZLFaO4IqEX3PSl4jPA1wxRbIas0TYBi6pQA" +
                "AABt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAVKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL07vagDT/Zs1d0xV/ZGv3T91QbiBCGO0SfEPaa" +
                "w23Wl4xfukvGwvzIqUysvHSh1VVyp/E8ZQYV0j7Gi7rs422YnxbHgAYIAwIGAAQEAAAACQAsWlRBRUFBQ" +
                "UFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQT0KBgMEAAAHCAnltTryqwjJkPYKAwMBAA" +
                "kscCGscRyODfYKBwMEAQUABwgJtxJGnJRtoSL2CgYDBAAABwgJq95e6SL6ygH2")


    private fun transaction(base64: String): Pair<SolanaTransaction, String> {
        return Pair(SolanaTransaction.fromList(base64.decodeBase64().toList())!!, base64)
    }

    fun mockPrivateTransfer(): Pair<SolanaTransaction, String> {
        return transaction("AqWNqWCdgbzlWTVZQB7+iBg52O9A8107s/pfQ/Z2FndWizwNXukZioklvScCgQTZFr2f3eg4ojfEvpiZqwm9+wAiQGg5UsZEf/DjuHrnZr7YxHl0dIZexmPtmpgOdI69G7YVGSk2rE3sLk+65GeFUoDhpq7tzxP9W6nWzI5/5HQHAgEGCwksDha4qmHvDLlGQXd2cjb/PDR7UoWkLijNmnwnO1nuu7Xnafr2nnC0//MZTieqGWg8ygCot6SYJVjyndZCxGoaDXxcmpoifit1bsGjzYQ/vWUcn2k/tEUOjKQmrm5eKRu4eednzoBrNKpxRazVlEwA0hfWw9AnV3fGaPVtKli874Pwj2BfKXqwWqg0L7RsCDBgdqH2i6hMyNuRTnhIR1sGp9UXGSxWjuCKhF9z0peIzwNcMUWyGrNE2AYuqUAAAAbd9uHXZaGT2cvhRs7reawctIXtX1s3kTqM9YV+/wCp58J2lG1wb0LqCFPN9+Fla+HyGz75GCqQaZxuRH8yyfEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL07vagDT/Zr02LPzD6xb5Nf2x4+R0n7wWJyKAfah6AyAlWXFf4ixn10XVBBNgH+xMmt/5cXJ6W7m1U9liIi4o8EvBHfAMIAwQFAAQEAAAACQAsWlRBRUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQT0KCAcCAQADAAYIEUSA3sCBRUel/0ANAwAAAAAA")
    }

    fun mockCloseEmptyAccount(): Pair<SolanaTransaction, String> {
        return transaction("AliNx0Qd/Yh3rxDOm7tP6Nk5F/kIqkBCgUxtiPXbPa4hI/lPvnFu2R1kOUPSVyXfukFkhWVmVMbAhPsC1julZQZgPnBJozuXsURdMLy8FyhML7D4H0v1fhCHQJMOoAORJO75IeFbekkBWFFZZK+TOhBApSCQK4uEdjv7lyhK7dEDAgEECgksDha4qmHvDLlGQXd2cjb/PDR7UoWkLijNmnwnO1nuAwAkc+jnVXSj6mVi3qDSxmtMOwdDpQZWwcrlIreF0/0B6uU3GHXrot2xFmpofLZ+RJ3k0x3D0yyzG7HtV5blAgszOKCrLMhB1bAUvGo891YpGHSzGclRfZu/qeTpZh75/IUvnzG1DltbSH1irqx2Cyh7SroxvAgiqc8rXoqDGHz/5wv1HyDU2Q6Ue0j8NoyFFNuy3gehalCj+lmXjCqUuQan1RcZLFaO4IqEX3PSl4jPA1wxRbIas0TYBi6pQAAABt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA0/2a9Niz8w+sW+TX9sePkdJ+8FicigH2oegMgJVlxXnFoL9tCTU/3A8XPsFV4gd1BbGTdFQhiF05WPGERPmJIDCAMFBgAEBAAAAAkIBAIBAAMABwgRJyr/2g58Ti3/oIYBAAAAAAAJBgQCAAAHCAmr3l7pIvrKAf8=")
    }

}