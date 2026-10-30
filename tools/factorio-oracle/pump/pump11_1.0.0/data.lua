local base=data.raw.pump.pump
local fast=table.deepcopy(base);fast.name="oracle-pump-fast";fast.fluid_wagon_connector_speed=1/32
local slow=table.deepcopy(base);slow.name="oracle-pump-slow";slow.fluid_wagon_connector_speed=1/128
local pole=table.deepcopy(data.raw["electric-pole"]["small-electric-pole"]);pole.name="oracle-isolated-pole";pole.maximum_wire_distance=1;pole.supply_area_distance=0.75;pole.minable=nil
local source=table.deepcopy(data.raw.pipe.pipe);source.name="oracle-fluid-source";source.fluid_box.base_area=250;source.minable=nil
log("PUMP11_PROTOTYPES vanilla_explicit_connector_speed="..tostring(base.fluid_wagon_connector_speed).." fast="..tostring(fast.fluid_wagon_connector_speed).." slow="..tostring(slow.fluid_wagon_connector_speed).." pumping_speed="..tostring(base.pumping_speed).." connector_frames="..tostring(base.fluid_wagon_connector_frame_count))
data:extend({fast,slow,pole,source})
