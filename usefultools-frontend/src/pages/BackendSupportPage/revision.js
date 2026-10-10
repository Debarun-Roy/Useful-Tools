// Cancellation saves work; identity checks also protect against uncancellable responses.
export function revisionGuard() {
  let revision = 0
  let request = 0
  return {
    edit() { revision++; request++ },
    start() { return { revision, request: ++request } },
    current(ticket) { return ticket.revision === revision && ticket.request === request },
  }
}
