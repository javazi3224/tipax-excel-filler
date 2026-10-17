package ir.tipaxrezashahr.excelfiller

/** یک ردیف اطلاعات گیرنده که قراره تو فایل اکسل تیپاکس پر بشه. */
data class ShipmentRow(
    val recipientName: String,
    val city: String,
    val address: String,
    val phone: String
)
