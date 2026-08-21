package com.barclub.entity;

public enum MetodoPago {
    EFECTIVO,
    TARJETA,
    TRANSFERENCIA,
    // El cliente pagó desde la web pública con Mercado Pago (ver
    // PagoOnlineController/MercadoPagoService), antes de que el cajero
    // cobre el pedido. La plata cae directo en la cuenta de Mercado Pago
    // del local, no pasa por la caja del sistema.
    MERCADOPAGO
}
