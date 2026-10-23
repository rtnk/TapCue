package maestro.cli.view

import maestro.cli.model.FlowStatus
import maestro.cli.util.PrintUtils
import maestro.cli.util.TimeUtils
import maestro.cli.view.TestSuiteStatusView.TestSuiteViewModel.FlowResult
import org.jline.jansi.Ansi
import kotlin.time.Duration

object TestSuiteStatusView {

    fun showFlowCompletion(result: FlowResult) {
        val shardPrefix = result.shardIndex?.let { "[shard ${it + 1}] " }.orEmpty()
        print(Ansi.ansi().fgCyan().render(shardPrefix).fgDefault())

        printStatus(result.status)

        val durationString = result.duration?.let { " (${TimeUtils.forDisplay(it)})" }.orEmpty()
        print(" ${result.name}$durationString")

        if (result.status == FlowStatus.ERROR && result.error != null) {
            val error = " (${result.error})"
            print(Ansi.ansi().fgRed().render(error).fgDefault())
        }
        else if (result.status == FlowStatus.WARNING) {
            val warning = " (Warning)"
            print(Ansi.ansi().fgYellow().render(warning).fgDefault())
        }
        println()
    }

    fun showSuiteResult(suite: TestSuiteViewModel) {
        val hasError = suite.flows.find { it.status == FlowStatus.ERROR } != null
        val canceledFlows = suite.flows
            .filter { it.status == FlowStatus.CANCELED }
        val shardPrefix = suite.shardIndex?.let { "[shard ${it + 1}] " }.orEmpty()

        if (suite.status == FlowStatus.ERROR || hasError) {
            val failedFlows = suite.flows
                .filter { it.status == FlowStatus.ERROR }

            PrintUtils.err(
                "${shardPrefix}${failedFlows.size}/${suite.flows.size} ${flowWord(failedFlows.size)} Failed",
                bold = true,
            )

            if (canceledFlows.isNotEmpty()) {
                PrintUtils.warn("${shardPrefix}${canceledFlows.size} ${flowWord(canceledFlows.size)} Canceled")
            }
        } else {
            val passedFlows = suite.flows
                .filter { it.status == FlowStatus.SUCCESS || it.status == FlowStatus.WARNING }


            if (passedFlows.isNotEmpty()) {
                val durationMessage = suite.duration?.let { " in ${TimeUtils.forDisplay(it)}" } ?: ""
                PrintUtils.success(
                    "${shardPrefix}${passedFlows.size}/${suite.flows.size} ${flowWord(passedFlows.size)} Passed$durationMessage",
                    bold = true,
                )

                if (canceledFlows.isNotEmpty()) {
                    PrintUtils.warn("${shardPrefix}${canceledFlows.size} ${flowWord(canceledFlows.size)} Canceled")
                }
            } else {
                println()
                PrintUtils.err("${shardPrefix}All flows were canceled")
            }
        }
        println()
    }

    private fun printStatus(status: FlowStatus) {
        val color = when (status) {
            FlowStatus.SUCCESS,
            FlowStatus.WARNING -> Ansi.Color.GREEN
            FlowStatus.ERROR -> Ansi.Color.RED
            FlowStatus.STOPPED -> Ansi.Color.RED
            else -> Ansi.Color.DEFAULT
        }
        val title = when (status) {
            FlowStatus.SUCCESS,
            FlowStatus.WARNING -> "Passed"
            FlowStatus.ERROR -> "Failed"
            FlowStatus.PENDING -> "Pending"
            FlowStatus.RUNNING -> "Running"
            FlowStatus.STOPPED -> "Stopped"
            FlowStatus.PREPARING -> "Preparing Device"
            FlowStatus.INSTALLING -> "Installing App"
            FlowStatus.CANCELED -> "Canceled"
        }

        print(
            Ansi.ansi()
                .fgBright(color)
                .render("[$title]")
                .fgDefault()
        )
    }

    private fun flowWord(count: Int) = if (count == 1) "Flow" else "Flows"

    data class TestSuiteViewModel(
        val status: FlowStatus,
        val flows: List<FlowResult>,
        val duration: Duration? = null,
        val shardIndex: Int? = null,
    ) {

        data class FlowResult(
            val name: String,
            val status: FlowStatus,
            val duration: Duration? = null,
            val error: String? = null,
            val shardIndex: Int? = null,
        )
    }

}
