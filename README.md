<h1 align="center">
  <a href="https://github.com/pedidosya">
  	<img src="https://img.pystatic.com/pedidosya-logo.svg" alt="PedidosYa" width="200">
  </a>
  <br>
  <br>
  Ejercicio Carrito de Compras
  <br>
</h1>


## Contexto

En PedidosYa tenemos una parte de nuestra plataforma preparada para la venta de productos de supermercado. Actualmente se encuentra implementada una solución inicial que incluye el listado de productos disponbles y la posibilidad de agregar esos productos al carrito de compra.


<img src="./resources/shopping_cart_screens.png" />

## Stack

* Compose
* ViewModel
* Flows
* Retrofit
* Hilt


## Enunciado

> :warning:  &nbsp;&nbsp; **Aclaración**: Compartir de forma verbal y/o escrita

Ahora bien, estabamos viendo de poder incentivar esta nueva forma de vender productos, y se nos ocurrio como equipo armar un esquema de ofertas. Estas deberiamos poder verlas antes de llegar al carrito (es decir, a medida que agregamos productos).

Los tipos de promociones no son fijos y pueden ir cambiando en el tiempo, por ejemplo:
* 2x1, 4x3, etc
* Descuento fijo
  



## Características del ejercicio

El ejercicio definido en este repositorio tiene las siguientes características:
* Código base del proyecto para importarlo en Android Studio.
* Arquitectura mínima para la implementación de la aplicación.
* UI para mostrar el listado de productos y agregarlos al carrito de compras.
* UI para mostrar los productos incluidos en el carrito de compras y el precio total de cada producto.
* Integración con API REST para obtener los productos.

### Resolución

#### Implementación

* El ejercicio cuenta con código provisto por los entrevistadores pero puede contener malas implementaciones o modelos incompletos, en este caso existen dos opciones:
  * reconocerlos y proponer verbalmente una alternativa superada
  * corregirlos para luego utilizar esa lógica en la implementación del ejercicio
* El ejercicio cuenta con una interfaz CartManager utilizada para calcular el precio final del carrito de compras que debe ser implementada por el entrevistado
  
   

#### Aspectos Generales

* Comprender, definir y diseñar una solución al problema planteado
* Definir correctamente el problema (clases de dominio, lógica de negocio, responsabilidades)
* Clean code y readability, alta cohesión y bajo acoplamiento y código testeable.
* Cobertura unitaria de las funcionalidades claves, uso de test doubles
* Diseño e implementación de la UI relativa a la solución.
* Extras: estrategia para compartir información entre listado y detalle de carrito.
* Extras: feedback visual al agregar elemento y contar cantidad de elementos en la fila.
* Extras: diseño de servicios de backend que podrian estar involucrados en la solución.
