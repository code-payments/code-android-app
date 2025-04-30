package com.getcode.opencode.model.financial

import com.getcode.opencode.internal.extensions.getClosestLocale
import java.util.Currency

enum class CurrencyCode {
    AED,
    AFN,
    ALL,
    AMD,
    ANG,
    AOA,
    ARS,
    AUD,
    AWG,
    AZN,
    BAM,
    BBD,
    BDT,
    BGN,
    BHD,
    BIF,
    BMD,
    BND,
    BOB,
    BRL,
    BSD,
    BTN,
    BWP,
    BYN,
    BZD,
    CAD,
    CDF,
    CHF,
    CLP,
    CNY,
    COP,
    CRC,
    CUP,
    CVE,
    CZK,
    DJF,
    DKK,
    DOP,
    DZD,
    EGP,
    ERN,
    ETB,
    EUR,
    FJD,
    FKP,
    GBP,
    GEL,
    GHS,
    GIP,
    GMD,
    GNF,
    GTQ,
    GYD,
    HKD,
    HNL,
    HRK,
    HTG,
    HUF,
    IDR,
    ILS,
    INR,
    IQD,
    IRR,
    ISK,
    JMD,
    JOD,
    JPY,
    KES,
    KGS,
    KHR,
    KMF,
    KPW,
    KRW,
    KWD,
    KYD,
    KZT,
    LAK,
    LBP,
    LKR,
    LRD,
    LYD,
    MAD,
    MDL,
    MGA,
    MKD,
    MMK,
    MNT,
    MOP,
    MRU,
    MUR,
    MVR,
    MWK,
    MXN,
    MYR,
    MZN,
    NAD,
    NGN,
    NIO,
    NOK,
    NPR,
    NZD,
    OMR,
    PAB,
    PEN,
    PGK,
    PHP,
    PKR,
    PLN,
    PYG,
    QAR,
    RON,
    RSD,
    RUB,
    RWF,
    SAR,
    SBD,
    SCR,
    SDG,
    SEK,
    SGD,
    SHP,
    SLL,
    SOS,
    SRD,
    SSP,
    STN,
    SYP,
    SZL,
    THB,
    TJS,
    TMT,
    TND,
    TOP,
    TRY,
    TTD,
    TWD,
    TZS,
    UAH,
    UGX,
    USD,
    UYU,
    UZS,
    VES,
    VND,
    VUV,
    WST,
    XAF,
    XCD,
    XOF,
    XPF,
    YER,
    ZAR,
    ZMW;

    fun getRegion(): RegionCode? {
        return when (this) {
            USD -> RegionCode.US
            EUR -> RegionCode.EU
            CHF -> RegionCode.CH
            NZD -> RegionCode.NZ
            XCD -> RegionCode.AG
            ZAR -> RegionCode.ZA
            DKK -> RegionCode.DK
            GBP -> RegionCode.GB
            ANG -> RegionCode.AN
            XPF -> RegionCode.PF
            MAD -> RegionCode.MA
            XAF -> null
            AUD -> RegionCode.AU
            NOK -> RegionCode.NO
            ILS -> RegionCode.IL
            XOF -> null
            BDT -> RegionCode.BD
            GTQ -> RegionCode.GT
            GYD -> RegionCode.GY
            AFN -> RegionCode.AF
            KYD -> RegionCode.KY
            BBD -> RegionCode.BB
            KES -> RegionCode.KE
            MVR -> RegionCode.MV
            EGP -> RegionCode.EG
            CRC -> RegionCode.CR
            HRK -> RegionCode.HR
            SGD -> RegionCode.SG
            BRL -> RegionCode.BR
            KGS -> RegionCode.KG
            SSP -> RegionCode.SS
            BTN -> RegionCode.BT
            PKR -> RegionCode.PK
            MMK -> RegionCode.MM
            MRU -> RegionCode.MR
            UZS -> RegionCode.UZ
            STN -> RegionCode.ST
            LYD -> RegionCode.LY
            MZN -> RegionCode.MZ
            SLL -> RegionCode.SL
            TJS -> RegionCode.TJ
            HKD -> RegionCode.HK
            SHP -> RegionCode.SH
            MXN -> RegionCode.MX
            WST -> RegionCode.WS
            BOB -> RegionCode.BO
            IDR -> RegionCode.ID
            CDF -> RegionCode.CD
            BSD -> RegionCode.BS
            BMD -> RegionCode.BM
            HUF -> RegionCode.HU
            AZN -> RegionCode.AZ
            PAB -> RegionCode.PA
            KZT -> RegionCode.KZ
            COP -> RegionCode.CO
            RUB -> RegionCode.RU
            QAR -> RegionCode.QA
            CUP -> RegionCode.CU
            AMD -> RegionCode.AM
            TOP -> RegionCode.TO
            SAR -> RegionCode.SA
            KPW -> RegionCode.KP
            NIO -> RegionCode.NI
            AOA -> RegionCode.AO
            ISK -> RegionCode.IS
            MNT -> RegionCode.MN
            MGA -> RegionCode.MG
            THB -> RegionCode.TH
            BYN -> RegionCode.BY
            BWP -> RegionCode.BW
            RSD -> RegionCode.RS
            CLP -> RegionCode.CL
            GMD -> RegionCode.GM
            AED -> RegionCode.AE
            TZS -> RegionCode.TZ
            ALL -> RegionCode.AL
            KHR -> RegionCode.KH
            IRR -> RegionCode.IR
            ETB -> RegionCode.ET
            PHP -> RegionCode.PH
            MDL -> RegionCode.MD
            SBD -> RegionCode.SB
            SDG -> RegionCode.SD
            VUV -> RegionCode.VU
            MKD -> RegionCode.MK
            HTG -> RegionCode.HT
            SRD -> RegionCode.SR
            BZD -> RegionCode.BZ
            BIF -> RegionCode.BI
            MYR -> RegionCode.MY
            PEN -> RegionCode.PE
            BHD -> RegionCode.BH
            RON -> RegionCode.RO
            UAH -> RegionCode.UA
            PYG -> RegionCode.PY
            TTD -> RegionCode.TT
            CAD -> RegionCode.CA
            SCR -> RegionCode.SC
            TRY -> RegionCode.TR
            VES -> RegionCode.VE
            FKP -> RegionCode.FK
            HNL -> RegionCode.HN
            GNF -> RegionCode.GN
            NGN -> RegionCode.NG
            MWK -> RegionCode.MW
            ERN -> RegionCode.ER
            SZL -> RegionCode.SZ
            BGN -> RegionCode.BG
            MOP -> RegionCode.MO
            SEK -> RegionCode.SE
            BND -> RegionCode.BN
            FJD -> RegionCode.FJ
            KWD -> RegionCode.KW
            CZK -> RegionCode.CZ
            TWD -> RegionCode.TW
            DOP -> RegionCode.DO
            DJF -> RegionCode.DJ
            JPY -> RegionCode.JP
            OMR -> RegionCode.OM
            LRD -> RegionCode.LR
            KMF -> RegionCode.KM
            MUR -> RegionCode.MU
            JMD -> RegionCode.JM
            TND -> RegionCode.TN
            LBP -> RegionCode.LB
            TMT -> RegionCode.TM
            JOD -> RegionCode.JO
            LKR -> RegionCode.LK
            UGX -> RegionCode.UG
            SOS -> RegionCode.SO
            NAD -> RegionCode.NA
            PLN -> RegionCode.PL
            AWG -> RegionCode.AW
            RWF -> RegionCode.RW
            LAK -> RegionCode.LA
            DZD -> RegionCode.DZ
            YER -> RegionCode.YE
            SYP -> RegionCode.SY
            UYU -> RegionCode.UY
            CNY -> RegionCode.CN
            KRW -> RegionCode.KR
            ARS -> RegionCode.AR
            GHS -> RegionCode.GH
            NPR -> RegionCode.NP
            INR -> RegionCode.IN
            IQD -> RegionCode.IQ
            BAM -> RegionCode.BA
            CVE -> RegionCode.CV
            GEL -> RegionCode.GE
            ZMW -> RegionCode.ZM
            GIP -> RegionCode.GI
            VND -> RegionCode.VN
            PGK -> RegionCode.PG
        }
    }

    companion object {
        fun tryValueOf(value: String?): CurrencyCode? {
            return try {
                valueOf(value?.uppercase().orEmpty())
            } catch (e: Exception) {
                null
            }
        }

        private val lookupTable: Map<CurrencyCode, Set<String>> by lazy {
            buildMap {
                CurrencyCode.entries.forEach { currency ->
                    val locale = currency.getClosestLocale()
                    try {
                        val currencyInstance = Currency.getInstance(currency.name)
                        val symbol = currencyInstance.getSymbol(locale)
                        if (symbol != null) {
                            put(currency, setOf(symbol))
                        }
                    } catch (e: IllegalArgumentException) {
                        // Skip currencies with no valid symbol
                    }
                }
            }
        }

        private val currenciesRegions: Map<CurrencyCode, RegionCode?> = mapOf(
            USD to RegionCode.US,
            EUR to RegionCode.EU,
            CHF to RegionCode.CH,
            NZD to RegionCode.NZ,
            XCD to RegionCode.AG,
            ZAR to RegionCode.ZA,
            DKK to RegionCode.DK,
            GBP to RegionCode.GB,
            ANG to RegionCode.AN,
            XPF to RegionCode.PF,
            MAD to RegionCode.MA,
            XAF to null,
            AUD to RegionCode.AU,
            NOK to RegionCode.NO,
            ILS to RegionCode.IL,
            XOF to null,
            BDT to RegionCode.BD,
            GTQ to RegionCode.GT,
            GYD to RegionCode.GY,
            AFN to RegionCode.AF,
            KYD to RegionCode.KY,
            BBD to RegionCode.BB,
            KES to RegionCode.KE,
            MVR to RegionCode.MV,
            EGP to RegionCode.EG,
            CRC to RegionCode.CR,
            HRK to RegionCode.HR,
            SGD to RegionCode.SG,
            BRL to RegionCode.BR,
            KGS to RegionCode.KG,
            SSP to RegionCode.SS,
            BTN to RegionCode.BT,
            PKR to RegionCode.PK,
            MMK to RegionCode.MM,
            MRU to RegionCode.MR,
            UZS to RegionCode.UZ,
            STN to RegionCode.ST,
            LYD to RegionCode.LY,
            MZN to RegionCode.MZ,
            SLL to RegionCode.SL,
            TJS to RegionCode.TJ,
            HKD to RegionCode.HK,
            SHP to RegionCode.SH,
            MXN to RegionCode.MX,
            WST to RegionCode.WS,
            BOB to RegionCode.BO,
            IDR to RegionCode.ID,
            CDF to RegionCode.CD,
            BSD to RegionCode.BS,
            BMD to RegionCode.BM,
            HUF to RegionCode.HU,
            AZN to RegionCode.AZ,
            PAB to RegionCode.PA,
            KZT to RegionCode.KZ,
            COP to RegionCode.CO,
            RUB to RegionCode.RU,
            QAR to RegionCode.QA,
            CUP to RegionCode.CU,
            AMD to RegionCode.AM,
            TOP to RegionCode.TO,
            SAR to RegionCode.SA,
            KPW to RegionCode.KP,
            NIO to RegionCode.NI,
            AOA to RegionCode.AO,
            ISK to RegionCode.IS,
            MNT to RegionCode.MN,
            MGA to RegionCode.MG,
            THB to RegionCode.TH,
            BYN to RegionCode.BY,
            BWP to RegionCode.BW,
            RSD to RegionCode.RS,
            CLP to RegionCode.CL,
            GMD to RegionCode.GM,
            AED to RegionCode.AE,
            TZS to RegionCode.TZ,
            ALL to RegionCode.AL,
            KHR to RegionCode.KH,
            IRR to RegionCode.IR,
            ETB to RegionCode.ET,
            PHP to RegionCode.PH,
            MDL to RegionCode.MD,
            SBD to RegionCode.SB,
            SDG to RegionCode.SD,
            VUV to RegionCode.VU,
            MKD to RegionCode.MK,
            HTG to RegionCode.HT,
            SRD to RegionCode.SR,
            BZD to RegionCode.BZ,
            BIF to RegionCode.BI,
            MYR to RegionCode.MY,
            PEN to RegionCode.PE,
            BHD to RegionCode.BH,
            RON to RegionCode.RO,
            UAH to RegionCode.UA,
            PYG to RegionCode.PY,
            TTD to RegionCode.TT,
            CAD to RegionCode.CA,
            SCR to RegionCode.SC,
            TRY to RegionCode.TR,
            VES to RegionCode.VE,
            FKP to RegionCode.FK,
            HNL to RegionCode.HN,
            GNF to RegionCode.GN,
            NGN to RegionCode.NG,
            MWK to RegionCode.MW,
            ERN to RegionCode.ER,
            SZL to RegionCode.SZ,
            BGN to RegionCode.BG,
            MOP to RegionCode.MO,
            SEK to RegionCode.SE,
            BND to RegionCode.BN,
            FJD to RegionCode.FJ,
            KWD to RegionCode.KW,
            CZK to RegionCode.CZ,
            TWD to RegionCode.TW,
            DOP to RegionCode.DO,
            DJF to RegionCode.DJ,
            JPY to RegionCode.JP,
            OMR to RegionCode.OM,
            LRD to RegionCode.LR,
            KMF to RegionCode.KM,
            MUR to RegionCode.MU,
            JMD to RegionCode.JM,
            TND to RegionCode.TN,
            LBP to RegionCode.LB,
            TMT to RegionCode.TM,
            JOD to RegionCode.JO,
            LKR to RegionCode.LK,
            UGX to RegionCode.UG,
            SOS to RegionCode.SO,
            NAD to RegionCode.NA,
            PLN to RegionCode.PL,
            AWG to RegionCode.AW,
            RWF to RegionCode.RW,
            LAK to RegionCode.LA,
            DZD to RegionCode.DZ,
            YER to RegionCode.YE,
            SYP to RegionCode.SY,
            UYU to RegionCode.UY,
            CNY to RegionCode.CN,
            KRW to RegionCode.KR,
            ARS to RegionCode.AR,
            GHS to RegionCode.GH,
            NPR to RegionCode.NP,
            INR to RegionCode.IN,
            IQD to RegionCode.IQ,
            BAM to RegionCode.BA,
            CVE to RegionCode.CV,
            GEL to RegionCode.GE,
            ZMW to RegionCode.ZM,
            GIP to RegionCode.GI,
            VND to RegionCode.VN,
            PGK to RegionCode.PG,
        )

        val regionsCurrencies: Map<RegionCode, CurrencyCode> =
            currenciesRegions
                .mapNotNull { p -> p.value?.let { v -> Pair(v, p.key) } }
                .toMap()
    }

    val currencySymbols: List<String>
        get() = lookupTable[this]?.toList()?.sortedBy { it.length } ?: emptyList()

    val singleCharacterCurrencySymbol: String?
        get() = lookupTable[this]?.firstOrNull { it.length == 1 }
}

