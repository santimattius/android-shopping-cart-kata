# kata-cross-2026

# Desafío Técnico: Carrito de Compras en Android (Kotlin)

## 📌 Contexto del Ejercicio
El objetivo de esta prueba es desarrollar una aplicación de carrito de compras utilizando **Kotlin** y componentes modernos de Android (**Jetpack Compose**). Buscamos evaluar tus habilidades de arquitectura, buenas prácticas de desarrollo, manejo de estado y persistencia de datos.

> 🤖 **Nota sobre el uso de IA:** Está totalmente permitido (y se incentiva) el uso de herramientas de Inteligencia Artificial (GitHub Copilot, ChatGPT, Claude, etc.) durante el proceso de desarrollo. En la entrevista técnica posterior, conversaremos sobre los *workflows* aplicados, los *prompts* utilizados y cómo integraste la IA en tu flujo de trabajo de manera crítica y eficiente.

---

## 🛠 Requerimientos Técnicos

### 1. Pantalla Inicial: Lista de Ítems del Carrito
Deberás conectarte a una URL remota que devuelva el listado de ítems del carrito. Esta pantalla debe cumplir con:
* **Manejo de Estados:** Implementar estados visuales de *Loading*, *Error* y un mecanismo de *Retry* (reintento).
* **Estrategia de Caché (Offline First):** La aplicación debe almacenar los datos localmente (ej. Room, DataStore) para permitir su apertura incluso sin conexión a internet.
    * Al iniciar la app, se deben mostrar inmediatamente los datos guardados en caché.
    * En segundo plano, se debe realizar la petición de red para actualizar la vista de forma transparente una vez que finalice.
    * Si es la primera carga y no existen datos (ni en caché ni en red), se debe mostrar un *Loading*.
* **Plus (*Extra Mile*):** Implementación de *Pull-to-Refresh* (Swipe Refresh) para forzar la actualización manual de la lista.

### 2. Aplicación de Cupones y Validación
En la parte inferior se debe incluir:
* Un campo de texto para ingresar un cupón de descuento.
* Un botón de **Confirmar Compra**.
* **Servicio de Cupones:** La validación de los cupones y su porcentaje de descuento asociado debe resolverse mediante un servicio que interactúe con el mock local o la url remota.

### 3. Pantalla de Resumen (Summary)
Al presionar "Confirmar Compra", la app debe navegar a una pantalla secundaria que muestre:
* El monto total final a pagar.
* El porcentaje de descuento aplicado (en caso de que el cupón haya sido válido).

---

## 🕸️ URLs mock
* [GET cart](https://dummyjson.com/c/9518-ea8d-4660-afd8)
* [GET coupons](https://dummyjson.com/c/8e56-250c-4cd8-9c5a)

---

## 📐 ¿Qué evaluaremos?

* **Arquitectura y Patrones de Diseño:** El patrón elegido (MVVM, MVI, Clean Architecture, etc.) y la correcta separación de responsabilidades.
* **Comunicación entre Capas:** Los mecanismos utilizados para la reactividad y comunicación de datos (Coroutines, Flow, StateFlow, LiveData, interfaces, etc.).
* **Modelado de Datos:** La claridad, robustez y tipado del modelo de dominio elegido (uso adecuado de `data classes`, `sealed classes`/`interfaces`).
* **Robustez de Producto:** Manejo correcto de casos de borde (errores de red, listas vacías, comportamiento offline).
* **Workflow con IA:** Tu capacidad para potenciar el desarrollo utilizando asistentes de código, analizando las soluciones sugeridas y justificando las decisiones tomadas.
