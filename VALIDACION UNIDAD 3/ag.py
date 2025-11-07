import random      # Para funciones de aleatoriedad (mutación, selección)
import numpy as np   # Para cálculos numéricos (distancia)
import pandas as pd  # Para la selección por ruleta
import operator    # Para ordenar los resultados de aptitud
import time        # Para medir el tiempo

# --- CLASE 1: Municipio ---
# Define un "lugar" o "ciudad" en un mapa.
# Cada municipio tiene coordenadas X e Y.
class municipio:
    # Constructor: se llama al crear un nuevo objeto municipio
    def __init__(self, x, y):
        self.x = x
        self.y = y
    
    # Método para calcular la distancia a otro municipio
    def distancia(self, municipio):
        # Usa el Teorema de Pitágoras para la distancia euclidiana
        xDis = abs(self.x - municipio.x)
        yDis = abs(self.y - municipio.y)
        distancia = np.sqrt((xDis ** 2) + (yDis ** 2))
        return distancia

    def __repr__(self):
        return "(" + str(self.x) + "," + str(self.y) + ")"


# --- CLASE 2: Aptitud (Fitness) ---
# Evalúa qué tan "buena" (apta) es una ruta completa.
# Una ruta es "mejor" si su distancia total es "menor".
class Aptitud:
    def __init__(self, ruta):
        self.ruta = ruta      # La ruta es una lista de objetos 'municipio'
        self.distancia = 0
        self.f_aptitud = 0.0  # La aptitud será 1 / distancia
    
    # Método para calcular la distancia total de la ruta
    def distanciaRuta(self):
        if self.distancia == 0:
            distanciaRelativa = 0
            # Recorrer cada "parada" (municipio) en la ruta
            for i in range(0, len(self.ruta)):
                puntoInicial = self.ruta[i]
                puntoFinal = None
                
                # Si no es la última ciudad, ir a la siguiente de la lista
                if i + 1 < len(self.ruta):
                    puntoFinal = self.ruta[i + 1]
                else:
                    # Si es la última ciudad, debe regresar al punto de inicio
                    puntoFinal = self.ruta[0]
                
                # Sumar la distancia de este segmento
                distanciaRelativa += puntoInicial.distancia(puntoFinal)
            self.distancia = distanciaRelativa
        return self.distancia
    
    # Método para calcular la "aptitud" (fitness)
    def rutaApta(self):
        if self.f_aptitud == 0:
            # La aptitud es la inversa de la distancia.
            # (Menor distancia = Mayor aptitud)
            self.f_aptitud = 1 / float(self.distanciaRuta())
        return self.f_aptitud

# --- PASO 1: INICIALIZACIÓN ---
# Crea una "ruta" (individuo) aleatoria.
# Una ruta es simplemente una lista de ciudades en un orden aleatorio.
def crearRuta(listaMunicipios):
    # 'random.sample' crea una copia barajada de la lista
    ruta = random.sample(listaMunicipios, len(listaMunicipios))
    return ruta

# Crea la "población inicial" (un grupo de rutas aleatorias).
def poblacionInicial(tamanoPob, listaMunicipios):
    poblacion = []
    # Crear 'tamanoPob' rutas aleatorias
    for i in range(0, tamanoPob):
        poblacion.append(crearRuta(listaMunicipios))
    return poblacion

# --- PASO 2: EVALUACIÓN (FITNESS) ---
# Evalúa todas las rutas en la población y las ordena de mejor a peor.
def clasificacionRutas(poblacion):
    fitnessResults = {} # Un diccionario para guardar (indice, aptitud)
    # Asignar una aptitud a cada ruta
    for i in range(0, len(poblacion)):
        fitnessResults[i] = Aptitud(poblacion[i]).rutaApta()
    
    # Devuelve una lista ordenada (la más alta aptitud primero)
    return sorted(fitnessResults.items(), key=operator.itemgetter(1), reverse=True)

# --- PASO 3: SELECCIÓN ---
# Selecciona qué individuos (rutas) pasarán a la siguiente generación.
# Usa "Elitismo" (los mejores pasan directo) y "Selección por Ruleta".
def seleccionRutas(popRanked, indivSelecionados):
    resultadosSeleccion = []
    
    # Configuración para la "Selección por Ruleta" (usando pandas)
    df = pd.DataFrame(np.array(popRanked), columns=["Indice", "Aptitud"])
    df['cum_sum'] = df.Aptitud.cumsum()
    df['cum_perc'] = 100 * df.cum_sum / df.Aptitud.sum()
    
    # 1. Elitismo: Los 'indivSelecionados' (élite) mejores pasan automáticamente
    for i in range(0, indivSelecionados):
        resultadosSeleccion.append(popRanked[i][0])
    
    # 2. Selección por Ruleta: Elegir al azar al resto de la población
    for i in range(0, len(popRanked) - indivSelecionados):
        seleccion = 100 * random.random()
        for i in range(0, len(popRanked)):
            # Gira la "ruleta" y elige un individuo basado en su aptitud
            if seleccion <= df.iat[i, 3]:
                resultadosSeleccion.append(popRanked[i][0])
                break
    return resultadosSeleccion

# Agrupa las rutas seleccionadas que se usarán para el apareamiento.
def grupoApareamiento(poblacion, resultadosSeleccion):
    grupoApareamiento = []
    for i in range(0, len(resultadosSeleccion)):
        index = resultadosSeleccion[i]
        grupoApareamiento.append(poblacion[index])
    return grupoApareamiento

# --- PASO 4: CRUCE (REPRODUCCIÓN) ---
# Crea un "hijo" (nueva ruta) a partir de dos "padres".
def reproduccion(progenitor1, progenitor2):
    hijo = []
    hijoP1 = []
    hijoP2 = []
    
    # Elige dos puntos aleatorios en la ruta
    generacionX = int(random.random() * len(progenitor1))
    generacionY = int(random.random() * len(progenitor2))
    
    generacionInicial = min(generacionX, generacionY)
    generacionFinal = max(generacionX, generacionY)

    # Tomar el "segmento" genético del padre 1
    for i in range(generacionInicial, generacionFinal):
        hijoP1.append(progenitor1[i])
    
    # Tomar las ciudades restantes del padre 2, en el orden en que aparecen
    hijoP2 = [item for item in progenitor2 if item not in hijoP1]

    # El hijo es la combinación de ambos
    hijo = hijoP1 + hijoP2
    return hijo

# Crea la población de "hijos" (la nueva generación).
def reproduccionPoblacion(grupoApareamiento, indivSelecionados):
    hijos = []
    tamano = len(grupoApareamiento) - indivSelecionados
    espacio = random.sample(grupoApareamiento, len(grupoApareamiento))

    # 1. Elitismo: Los 'indivSelecionados' (élite) pasan directo, sin cambios
    for i in range(0, indivSelecionados):
        hijos.append(grupoApareamiento[i])
    
    # 2. Cruce: Aparear al resto para crear 'tamano' nuevos hijos
    for i in range(0, tamano):
        hijo = reproduccion(espacio[i], espacio[len(grupoApareamiento) - i - 1])
        hijos.append(hijo)
    return hijos

# --- PASO 5: MUTACIÓN ---
# Aplica una mutación aleatoria a un individuo (ruta).
# Simplemente intercambia dos ciudades de lugar.
def mutacion(individuo, razonMutacion):
    for swapped in range(len(individuo)):
        # Si un número aleatorio es menor que la 'razonMutacion'
        if(random.random() < razonMutacion):
            # Elegir otra ciudad aleatoria para intercambiar
            swapWith = int(random.random() * len(individuo))
            
            lugar1 = individuo[swapped]
            lugar2 = individuo[swapWith]
            
            individuo[swapped] = lugar2
            individuo[swapWith] = lugar1
    return individuo
# Ejecuta la mutación sobre la población.
# ¡Debe saltarse a la élite para que el elitismo funcione!
def mutacionPoblacion(poblacion, razonMutacion, indivSelecionados):
    pobMutada = []
    
    # 1. Conservar la élite (los primeros 'indivSelecionados') INTACTA
    for ind in range(0, indivSelecionados):
        pobMutada.append(poblacion[ind])
    
    # 2. Aplicar mutación SÓLO al resto de la población
    for ind in range(indivSelecionados, len(poblacion)):
        individuoMutar = mutacion(poblacion[ind], razonMutacion)
        pobMutada.append(individuoMutar)
    return pobMutada

# --- FUNCIÓN DE CICLO: NUEVA GENERACIÓN ---
# Ejecuta un ciclo completo del AG: Evaluar, Seleccionar, Cruzar, Mutar.
def nuevaGeneracion(generacionActual, indivSelecionados, razonMutacion):

    # 1. Clasificar rutas (Evaluar)
    popRanked = clasificacionRutas(generacionActual)

    # 2. Seleccion de los candidatos
    selectionResults = seleccionRutas(popRanked, indivSelecionados)

    # 3. Generar grupo de apareamiento
    grupoApa = grupoApareamiento(generacionActual, selectionResults)

    # 4. Generacion de la poblacion cruzada (Reproducir)
    hijos = reproduccionPoblacion(grupoApa, indivSelecionados)

    # 5. Incluir las mutaciones en la nueva generación
    # Se pasa 'indivSelecionados' para proteger a la élite de la mutación.
    nuevaGeneracion = mutacionPoblacion(hijos, razonMutacion, indivSelecionados)

    return nuevaGeneracion

# --- FUNCIÓN PRINCIPAL: EL ALGORITMO GENÉTICO ---

# Orquesta todo el proceso durante N 'generaciones'.
def algoritmoGenetico(poblacion, tamanoPoblacion, indivSelecionados, razonMutacion, generaciones):
    
    # 1. Crear la población inicial aleatoria
    pop = poblacionInicial(tamanoPoblacion, poblacion)
    
    # Imprimir la mejor distancia de la población inicial (solo para comparar)
    distancia_inicial = 1 / clasificacionRutas(pop)[0][1]
    print("Distancia Inicial (Mejor ruta): " + str(distancia_inicial))
    
    # 2. Evolucionar la población durante 'generaciones'
    for i in range(0, generaciones):
        pop = nuevaGeneracion(pop, indivSelecionados, razonMutacion)
    
    # 3. Al final, imprimir los resultados
    distancia_final = 1 / clasificacionRutas(pop)[0][1]
    print("Distancia Final (Mejor ruta):   " + str(distancia_final))
    
    # 4. Devolver la mejor ruta encontrada
    bestRouteIndex = clasificacionRutas(pop)[0][0]
    mejorRuta = pop[bestRouteIndex]
    return mejorRuta




# Creamos el "dataset" de ciudades con coordenadas (X, Y).

listaMunicipios = []

# (Nombre, x, y) - Coordenadas simples en un plano
datos_ciudades = [
    ("Londres", 5, 10),   # Arriba al centro
    ("Dublín", 1, 9),     # Izquierda de Londres
    ("París", 5, 7),      # Abajo de Londres
    ("Varsovia", 9, 9),   # Derecha de Londres
    ("Madrid", 3, 3),     # Abajo a la izquierda
    ("Atenas", 7, 1),     # Abajo al centro
    ("Moscú", 11, 6)      # Derecha
]

# Convertir los datos en "objetos" municipio que el código entiende
for nombre, x, y in datos_ciudades:
    listaMunicipios.append(municipio(x, y))

# 2. Definir los parámetros del Algoritmo Genético
TAMANO_POBLACION = 100   # Cuántas rutas (individuos) por generación
INDIV_SELECCIONADOS = 20 # Cuántos individuos "élite" (los mejores) sobreviven
RAZON_MUTACION = 0.01    # 1% de probabilidad de que una ciudad mute
GENERACIONES = 500       # Cuántas veces evolucionará la población

# 3. Llamar a la función principal para que se ejecute todo
print("Iniciando Algoritmo Genético para el Problema del Viajante...")

# Medir el tiempo de ejecución
inicio = time.time()

mejor_ruta = algoritmoGenetico(
    poblacion=listaMunicipios, 
    tamanoPoblacion=TAMANO_POBLACION, 
    indivSelecionados=INDIV_SELECCIONADOS,
    razonMutacion=RAZON_MUTACION, 
    generaciones=GENERACIONES
)

fin = time.time()
print(f"Tiempo total de ejecución: {fin - inicio:.2f} segundos")

# 4. Imprimir el resultado final de forma legible
print("\n--- ¡Proceso completado! ---")
print("La mejor ruta encontrada visita las ciudades en este orden:")

# Construir el string de la ruta para mostrarlo
ruta_str = "INICIO -> "
for punto in mejor_ruta:
    ruta_str += str(punto) + " -> "
ruta_str += "FIN (Regreso a " + str(mejor_ruta[0]) + ")"

print(ruta_str)