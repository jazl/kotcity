package kotcity.ui

import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import kotcity.data.BlockCoordinate
import kotcity.data.CityMap
import kotcity.data.TileType

class CityRenderer(private val gameFrame: GameFrame, private val canvas: ResizableCanvas, private val map: CityMap) {

    private fun bleach(color: Color, amount: Float): Color {
        var red = (color.red + amount).coerceIn(0.0, 1.0)
        var green = (color.green + amount).coerceIn(0.0, 1.0)
        var blue = (color.blue + amount).coerceIn(0.0, 1.0)
        return Color.color(red, green, blue)
    }

    var zoom = 1.0
    var blockOffsetX = 0.0
    var blockOffsetY = 0.0
    var mapMin = 0.0
    var mapMax = 1.0

    private var mouseDown = false
    private var mouseBlock: BlockCoordinate? = null
    private var firstBlockPressed: BlockCoordinate? = null

    init {
        mapMin = map.groundLayer.values.mapNotNull {it.elevation}.min() ?: 0.0
        mapMax = map.groundLayer.values.mapNotNull {it.elevation}.max() ?: 0.0

        println("Map min: $mapMin Map max: $mapMax")
        println("Map has been set to: $map. Size is ${canvas.width}x${canvas.height}")

    }

    private fun canvasBlockHeight() = (canvas.height / blockSize()).toInt()

    private fun canvasBlockWidth() = (canvas.width / blockSize()).toInt()

    private fun getVisibleBlocks(): Pair<IntRange, IntRange> {
        var startBlockX = blockOffsetX.toInt()
        var startBlockY = blockOffsetY.toInt()
        var endBlockX = startBlockX+canvasBlockWidth()
        var endBlockY = startBlockY+canvasBlockHeight()

        if (endBlockX > map.width) {
            endBlockX = map.width
        }

        if (endBlockY > map.height) {
            endBlockY = map.height
        }

        return Pair(startBlockX..endBlockX, startBlockY..endBlockY)
    }

    private fun panMap(clickedBlock: BlockCoordinate) {
        // OK, we want to figure out the CENTER block now...
        val centerX = blockOffsetX + (canvasBlockWidth() / 2)
        val centerY = blockOffsetY + (canvasBlockHeight() / 2)
        println("The center block is: $centerX,$centerY")
        println("We clicked at: ${clickedBlock.x},${clickedBlock.y}")
        val dx = clickedBlock.x - centerX
        val dy = clickedBlock.y - centerY
        println("Delta is: $dx,$dy")
        blockOffsetX += (dx)
        blockOffsetY += (dy)
    }

    private fun mouseToBlock(mouseX: Double, mouseY: Double): BlockCoordinate {
        // OK... this should be pretty easy...
        // println("Block offsets: ${blockOffsetX.toInt()},${blockOffsetY.toInt()}")
        val blockX =  (mouseX / blockSize()).toInt()
        val blockY =  (mouseY / blockSize()).toInt()
        // println("Mouse block coords: $blockX,$blockY")
        return BlockCoordinate(blockX + blockOffsetX.toInt(), blockY + blockOffsetY.toInt())
    }

    fun onMousePressed(evt: MouseEvent) {
        this.mouseDown = true
        this.firstBlockPressed = mouseToBlock(evt.x, evt.y)
        this.mouseBlock = this.firstBlockPressed
        println("Pressed on block: $firstBlockPressed")
    }

    fun onMouseReleased(evt: MouseEvent) {
        this.mouseDown = false
    }

    fun onMouseDragged(evt: MouseEvent) {
        val mouseX = evt.x
        val mouseY = evt.y
        val blockCoordinate = mouseToBlock(mouseX, mouseY)
        this.mouseBlock = blockCoordinate
        // println("The mouse is at $blockCoordinate")
    }

    fun onMouseClicked(evt: MouseEvent) {
        if (evt.button == MouseButton.SECONDARY) {
            val clickedBlock = mouseToBlock(evt.x, evt.y)
            panMap(clickedBlock)
        }
    }

    private fun drawMap(gc: GraphicsContext) {
        // we got that map...
        val (xRange, yRange) = getVisibleBlocks()

        xRange.toList().forEachIndexed { xi, x ->
            yRange.toList().forEachIndexed { yi, y ->
                val tile = map.groundLayer[BlockCoordinate(x, y)]
                if (tile != null) {
                    var newColor =
                            if (tile.type == TileType.GROUND) {
                                Color.rgb(153,102, 0)
                            } else {
                                Color.DARKBLUE
                            }
                    // this next line maps the elevations from -0.5 to 0.5 so we don't get
                    // weird looking colors....
                    val bleachAmount = Algorithms.scale(tile.elevation, mapMin, mapMax, -0.5, 0.5)
                    gc.fill = bleach(newColor, bleachAmount.toFloat())

                    val blockSize = blockSize()

                    gc.fillRect(
                            xi * blockSize,
                            yi * blockSize,
                            blockSize, blockSize
                    )

                    if (DRAW_GRID && zoom >= 3.0) {
                        gc.fill = Color.BLACK
                        gc.strokeRect(xi * blockSize, yi * blockSize, blockSize, blockSize)
                    }
                }


            }
        }
    }

    private fun fillBlocks(g2d: GraphicsContext, blockX: Int, blockY: Int, width: Int, height: Int) {
        for (y in blockY until blockY + height) {
            for (x in blockX until blockX + width) {
                highlightBlock(g2d, x, y)
            }
        }
    }

    fun render() {
        canvas.graphicsContext2D.fill = Color.BLACK
        canvas.graphicsContext2D.fillRect(0.0,0.0, canvas.width, canvas.height)
        drawMap(canvas.graphicsContext2D)
        if (mouseDown) {
            if (gameFrame.activeTool == Tool.ROAD) {
                drawRoadBlueprint(canvas.graphicsContext2D)
            }
        }
    }

    private fun highlightBlock(g2d: GraphicsContext, x: Int, y: Int) {
        g2d.fill = Color.MAGENTA
        // gotta translate here...
        val tx = x - blockOffsetX
        val ty = y - blockOffsetY
        g2d.fillRect(tx * blockSize(), ty  * blockSize(), blockSize(), blockSize())
    }

    private fun highlightBlock(g2d: GraphicsContext, hoveredBlockX: Int, hoveredBlockY: Int, radius: Int) {
        val startBlockX = (hoveredBlockX - Math.floor((radius / 2).toDouble())).toInt()
        val startBlockY = (hoveredBlockY - Math.floor((radius / 2).toDouble())).toInt()
        val endBlockX = startBlockX + radius - 1
        val endBlockY = startBlockY + radius - 1

        for (y in startBlockY..endBlockY) {
            for (x in startBlockX..endBlockX) {
                highlightBlock(g2d, x, y)
            }
        }
    }

    // each block should = 10 meters, square...
    // 64 pixels = 10 meters
    private fun blockSize(): Double {
        // return (this.zoom * 64)
        return when (zoom) {
            1.0 -> 4.0
            2.0 -> 8.0
            3.0 -> 16.0
            4.0 -> 32.0
            5.0 -> 64.0
            else -> 64.0
        }
    }

    private fun drawRoadBlueprint(gc: GraphicsContext) {
        // figure out if we are more horizontal or vertical away from origin point
        gc.fill = (Color.YELLOW)
        val startBlock = firstBlockPressed ?: return
        val endBlock = mouseBlock ?: return
        val x = startBlock.x
        val y = startBlock.y
        var x2 = endBlock.x
        var y2 = endBlock.y

        if (Math.abs(x - x2) > Math.abs(y - y2)) {
            // building horizontally
            // now fuck around with y2 so it's at the same level as y1
            y2 = y

            if (x < x2) {
                fillBlocks(gc, x, y, Math.abs(x - x2) + 1, 1)
            } else {
                fillBlocks(gc, x2, y, Math.abs(x - x2) + 1, 1)
            }
        } else {
            // building vertically
            // now fuck around with x2 so it's at the same level as x1
            x2 = x

            if (y < y2) {
                fillBlocks(gc, x, y, 1, Math.abs(y - y2) + 1)
            } else {
                fillBlocks(gc, x, y2, 1, Math.abs(y - y2) + 1)
            }

        }

    }
}