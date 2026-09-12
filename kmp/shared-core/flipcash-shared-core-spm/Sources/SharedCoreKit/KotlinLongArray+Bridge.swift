import SharedCore

extension Array where Element == Int64 {
    /// Converts this array to a `KotlinLongArray` for calling into shared Kotlin.
    var kotlinLongArray: KotlinLongArray {
        let array = KotlinLongArray(size: Int32(count))
        for (index, value) in enumerated() {
            array.set(index: Int32(index), value: value)
        }
        return array
    }
}
