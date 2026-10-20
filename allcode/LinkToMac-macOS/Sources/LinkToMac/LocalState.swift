import SwiftUI

@propertyWrapper
public struct LocalState<Value>: DynamicProperty {
    @StateObject private var container: Container

    final class Container: ObservableObject {
        @Published var value: Value
        init(_ value: Value) { self.value = value }
    }

    public init(wrappedValue: Value) {
        _container = StateObject(wrappedValue: Container(wrappedValue))
    }

    public init() where Value: ExpressibleByNilLiteral {
        self.init(wrappedValue: nil)
    }

    public var wrappedValue: Value {
        get { container.value }
        nonmutating set { container.value = newValue }
    }

    public var projectedValue: Binding<Value> {
        Binding(
            get: { self.container.value },
            set: { self.container.value = $0 }
        )
    }
}






