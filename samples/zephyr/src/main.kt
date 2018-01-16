
@SymbolName("blinky")
external fun blinky(value: Int) 
/*
@SymbolName("device_get_binding")
external fun device_get_binding(port: Int) 

@SymbolName("gpio_pin_configure")
external fun gpio_pin_configure(port: Int) 
*/

val x = 7

/*
interface HasZ { val z: Int } 

val y = object : HasZ {
    override val z = 5
}
*/
fun main(args: Array<String>) {
    blinky(x)
}
