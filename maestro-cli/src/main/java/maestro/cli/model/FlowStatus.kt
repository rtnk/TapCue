package maestro.cli.model

enum class FlowStatus {
    PENDING,
    PREPARING,
    INSTALLING,
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELED,
    STOPPED,
    WARNING
}
